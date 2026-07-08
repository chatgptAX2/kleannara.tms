package com.company.module.wms.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SZF_GET_CONVERT_QTY 완전 동일 구현 — Flask _convert_qty() Java 포팅
 *
 * 단위환산 로직:
 *   qty(from_uom 기준) → to_uom 기준 수량 반환
 *   변환 불가 / 예외 시 null 반환 (0과 구분)
 *
 * MEASI 테이블 구조:
 *   WAREKY  : 물류센터 코드
 *   MEASKY  : SKU 코드
 *   UOMKEY  : 단위 코드 (EA, R, SOK, ...)
 *   QTPUOM  : 환산 분자 (기준단위 수량)
 *   QTAUOM  : 환산 분모 (해당 단위 수량)
 *   INDDFU  : 기준단위 지시자 ('V' = 기준단위)
 */
@Slf4j
public class ConvertQtyUtil {

    private ConvertQtyUtil() {}

    /**
     * 단위 환산 수행.
     *
     * @param jdbc    JdbcTemplate (MEASI 조회)
     * @param wareky  물류센터 코드
     * @param measky  SKU 코드
     * @param qty     변환할 수량 (from_uom 기준)
     * @param fromUom 입력 단위
     * @param toUom   목표 단위
     * @param skug05  제품군 코드 (예외 처리용)
     * @return 변환된 수량 (5자리 반올림), 변환 불가 시 null
     */
    public static Double convert(JdbcTemplate jdbc,
                                  String wareky, String measky,
                                  Double qty, String fromUom, String toUom,
                                  String skug05) {
        if (qty == null) return null;

        // 동일 단위면 그대로
        if (fromUom != null && fromUom.equals(toUom)) {
            return round5(qty);
        }

        // SKUG05 예외: 제품군 20은 R, SOK 환산 불가
        String sg = (skug05 == null) ? "" : skug05.trim();
        if ("20".equals(sg) && ("R".equals(toUom) || "SOK".equals(toUom))) {
            return null;
        }

        // MEASI 조회
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(
                "SELECT UOMKEY, QTPUOM, QTAUOM, INDDFU FROM MEASI WHERE WAREKY=? AND MEASKY=?",
                wareky, measky
            );
        } catch (Exception e) {
            log.warn("ConvertQtyUtil MEASI 조회 실패: wareky={}, measky={}, {}", wareky, measky, e.getMessage());
            return null;
        }

        if (rows == null || rows.isEmpty()) return null;

        // SKUG05='10' + toUom='EA' 예외: EA/SOK 비율 계산
        if ("10".equals(sg) && "EA".equals(toUom)) {
            try {
                double eaSum  = 0, sokSum = 0;
                for (Map<String, Object> r : rows) {
                    String uk = str(r.get("UOMKEY"));
                    double qa = dbl(r.get("QTAUOM"));
                    if ("EA".equals(uk))  eaSum  += qa;
                    if ("SOK".equals(uk)) sokSum += qa;
                }
                if (sokSum != 0) return round5(eaSum / sokSum);
            } catch (Exception ignored) {}
            return null;
        }

        // MEASI 맵 구성: uomkey → (qtpuom, qtauom)
        Map<String, double[]> uomMap = new HashMap<>();   // { uomkey: [qtpuom, qtauom] }
        String dUomkey = null;  // INDDFU='V' 기준단위

        for (Map<String, Object> r : rows) {
            String uk  = str(r.get("UOMKEY"));
            double qtp = dbl(r.get("QTPUOM"));
            double qta = dbl(r.get("QTAUOM"));
            String idf = str(r.get("INDDFU"));
            if ("V".equals(idf)) dUomkey = uk;
            if (!uk.isEmpty() && !uomMap.containsKey(uk)) {
                uomMap.put(uk, new double[]{qtp, qta});
            }
        }

        // 목표단위 환산비율
        if (!uomMap.containsKey(toUom)) return null;
        double[] cArr = uomMap.get(toUom);
        double cQtp = cArr[0], cQta = cArr[1];
        if (cQtp == 0 || cQta == 0) return null;
        double ratio = cQtp / cQta;  // 목표단위 환산비율

        try {
            double result;
            if (fromUom != null && fromUom.equals(dUomkey)) {
                // 입력단위 = 기준단위 → 직접 환산
                result = qty / ratio;
            } else {
                // 입력단위 → 기준단위로 먼저 변환 (SZF_GET_DEFAULT_QTY)
                if (!uomMap.containsKey(fromUom)) return null;
                double[] fArr = uomMap.get(fromUom);
                if (fArr[1] == 0) return null;
                double defaultQty = qty * (fArr[0] / fArr[1]);  // → 기준단위 수량
                result = defaultQty / ratio;
            }
            return round5(result);
        } catch (ArithmeticException | NullPointerException e) {
            return null;
        }
    }

    /** skug05 미제공 오버로드 */
    public static Double convert(JdbcTemplate jdbc,
                                  String wareky, String measky,
                                  Double qty, String fromUom, String toUom) {
        return convert(jdbc, wareky, measky, qty, fromUom, toUom, "");
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────

    private static double round5(double v) {
        return Math.round(v * 100000.0) / 100000.0;
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString().trim();
    }

    private static double dbl(Object v) {
        if (v == null) return 0.0;
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0.0; }
    }
}
