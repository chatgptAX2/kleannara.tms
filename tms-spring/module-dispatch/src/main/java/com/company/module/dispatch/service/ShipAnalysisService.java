package com.company.module.dispatch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 출고실적 분석 Service
 *
 * <p>PS배차(최적화)의 배차확정(SAP선적) 탭에서 배차확정되어 최종 SAP 전송이 완료된
 * 출고(납품) 결과를 분석/집계한다. "SAP 전송 완료"의 판정 기준은
 * {@code TMS_SHPDI.STKNUM}(SAP 선적번호)이 실제 채번(공백/NULL 아님)된 출고 라인이다.</p>
 *
 * <p>데이터 소스(Oracle 19C KNRAWMS, 모두 읽기 전용 집계):</p>
 * <ul>
 *   <li>TMS_SHPDI (SI) : 출고문서 아이템 — STKNUM/STDLNR/SVBELN/SKUKEY/DESC01/QTSHPO/UOMKEY</li>
 *   <li>TMS_SHPDH (SH) : 출고문서 헤더   — SHPOKY/RQSHPD(납품일)/DPTNKY(납품처)/SHPMTY(출하유형)</li>
 *   <li>TMS_PS_DISPATCH_H (PH) : 배차 헤더 — CARTYPE(차량 톤수), DISPATCH_NO = SI.STDLNR</li>
 *   <li>SKUMA (M) : 품목 마스터 — NETWGT(단중) → 중량(KG) 산출</li>
 *   <li>BZPTN (CT) : 거래처 — NAME01(납품처명)</li>
 * </ul>
 *
 * <p>기존 기능/화면에 영향을 주지 않는 신규 조회 전용 서비스이다.
 * 기존 {@link DashboardService} 와 동일한 tmsJdbcTemplate 패턴을 따른다.</p>
 */
@Slf4j
@Service
public class ShipAnalysisService {

    private final JdbcTemplate jdbcTemplate;

    public ShipAnalysisService(@Qualifier("tmsJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** SAP 전송완료 출고 공통 조인/필터(FROM + WHERE 앞부분). 기간 바인딩 2개(start,end) 사용. */
    private static final String BASE_FROM =
        "FROM KNRAWMS.TMS_SHPDI SI " +
        "JOIN KNRAWMS.TMS_SHPDH SH ON SI.SHPOKY = SH.SHPOKY " +
        "LEFT JOIN KNRAWMS.SKUMA M  ON M.SKUKEY  = SI.SKUKEY " +
        "LEFT JOIN KNRAWMS.BZPTN CT ON CT.PTNRKY = SH.DPTNKY AND CT.PTNRTY = 'CT' " +
        "LEFT JOIN KNRAWMS.TMS_PS_DISPATCH_H PH ON PH.DISPATCH_NO = SI.STDLNR ";

    /** SAP 전송완료(STKNUM 채번) + 기간 필터. (오라클 '' = NULL 함정 회피: <> ' ') */
    private static final String SAP_DONE_WHERE =
        "WHERE SI.STKNUM IS NOT NULL AND TRIM(SI.STKNUM) <> '' " +
        "  AND SH.RQSHPD >= ? AND SH.RQSHPD <= ? ";

    /** 라인 중량(KG) 식 — QTSHPO × NETWGT(단중). */
    private static final String LINE_KG = "SI.QTSHPO * COALESCE(M.NETWGT, 0)";

    // ══════════════════════════════════════════════════════════════
    //  1. 요약 KPI (SAP 전송완료 출고 기준)
    // ══════════════════════════════════════════════════════════════
    public Map<String, Object> getSummary(String dateFrom, String dateTo) {
        String[] r = resolveRange(dateFrom, dateTo, 30);
        String sql =
            "SELECT " +
            "  COUNT(DISTINCT SI.STKNUM) AS SHIPMENT_CNT, " +      // 선적(출고) 건수
            "  COUNT(DISTINCT SI.SVBELN) AS DELIVERY_CNT, " +      // 납품문서 수
            "  COUNT(DISTINCT SH.DPTNKY) AS PARTNER_CNT, " +       // 납품처 수
            "  COUNT(DISTINCT SH.RQSHPD) AS DAY_CNT, " +           // 출고 일수
            "  ROUND(SUM(" + LINE_KG + ")/1000, 1) AS TOTAL_TON, " + // 총 출고중량(톤)
            "  ROUND(SUM(SI.QTSHPO), 0) AS TOTAL_QTY " +           // 총 출고수량
            BASE_FROM + SAP_DONE_WHERE;
        Map<String, Object> row = safeQueryForMap(sql, r[0], r[1]);

        long shipmentCnt = toLong(row.get("SHIPMENT_CNT"));
        long dayCnt      = toLong(row.get("DAY_CNT"));
        double totalTon  = toDouble(row.get("TOTAL_TON"));
        double avgPerDay = dayCnt > 0 ? Math.round(totalTon / dayCnt * 10.0) / 10.0 : 0.0;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rangeFrom",    r[0]);
        out.put("rangeTo",      r[1]);
        out.put("shipmentCnt",  shipmentCnt);
        out.put("deliveryCnt",  toLong(row.get("DELIVERY_CNT")));
        out.put("partnerCnt",   toLong(row.get("PARTNER_CNT")));
        out.put("dayCnt",       dayCnt);
        out.put("totalTon",     totalTon);
        out.put("totalQty",     toLong(row.get("TOTAL_QTY")));
        out.put("avgTonPerDay", avgPerDay);
        return out;
    }

    // ══════════════════════════════════════════════════════════════
    //  2. 집계 — 지역별 / 톤수별 / 일자별 / 출하유형별
    // ══════════════════════════════════════════════════════════════
    public Map<String, Object> getAggregates(String dateFrom, String dateTo) {
        String[] r = resolveRange(dateFrom, dateTo, 30);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rangeFrom", r[0]);
        out.put("rangeTo",   r[1]);

        // ── 지역별 출고비율 (납품처코드 앞2자리) ──
        String regionSql =
            "SELECT NVL(SUBSTR(SH.DPTNKY,1,2),'기타') AS REGION, " +
            "  COUNT(DISTINCT SI.STKNUM) AS SHIP_CNT, " +
            "  ROUND(SUM(" + LINE_KG + ")/1000, 1) AS TON " +
            BASE_FROM + SAP_DONE_WHERE +
            "GROUP BY NVL(SUBSTR(SH.DPTNKY,1,2),'기타') ORDER BY TON DESC";
        out.put("region", safeQueryList(regionSql, r[0], r[1]));

        // ── 톤수(차량유형)별 출고 비중 ── (배차헤더 CARTYPE)
        String cartypeSql =
            "SELECT NVL(PH.CARTYPE,'미지정') AS CARTYPE, " +
            "  COUNT(DISTINCT SI.STKNUM) AS SHIP_CNT, " +
            "  ROUND(SUM(" + LINE_KG + ")/1000, 1) AS TON " +
            BASE_FROM + SAP_DONE_WHERE +
            "GROUP BY NVL(PH.CARTYPE,'미지정') ORDER BY TON DESC";
        out.put("cartype", safeQueryList(cartypeSql, r[0], r[1]));

        // ── 일자별 출고량 ── (누락일 0 채움)
        String dailySql =
            "SELECT SH.RQSHPD AS RQSHPD, " +
            "  COUNT(DISTINCT SI.STKNUM) AS SHIP_CNT, " +
            "  ROUND(SUM(" + LINE_KG + ")/1000, 1) AS TON " +
            BASE_FROM + SAP_DONE_WHERE +
            "GROUP BY SH.RQSHPD ORDER BY SH.RQSHPD";
        out.put("daily", normalizeDaily(safeQueryList(dailySql, r[0], r[1]), r[0], r[1]));

        // ── 출하유형별 분포 ──
        String shpmtySql =
            "SELECT NVL(SH.SHPMTY,'기타') AS SHPMTY, " +
            "  COUNT(DISTINCT SI.STKNUM) AS SHIP_CNT, " +
            "  ROUND(SUM(" + LINE_KG + ")/1000, 1) AS TON " +
            BASE_FROM + SAP_DONE_WHERE +
            "GROUP BY NVL(SH.SHPMTY,'기타') ORDER BY TON DESC";
        out.put("shpmty", safeQueryList(shpmtySql, r[0], r[1]));

        return out;
    }

    // ══════════════════════════════════════════════════════════════
    //  3. 예측(디지털 트윈) — 빈도 분석 + 이동평균 기반 다음날 출고량 예측
    // ══════════════════════════════════════════════════════════════
    public Map<String, Object> getForecast(String dateFrom, String dateTo) {
        String[] r = resolveRange(dateFrom, dateTo, 30);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rangeFrom", r[0]);
        out.put("rangeTo",   r[1]);

        // ── 차량(톤수)별 출고 빈도 — 선적 건수/총중량/점유율 ──
        String carFreqSql =
            "SELECT NVL(PH.CARTYPE,'미지정') AS CARTYPE, " +
            "  COUNT(DISTINCT SI.STKNUM) AS SHIP_CNT, " +
            "  ROUND(SUM(" + LINE_KG + ")/1000, 1) AS TON " +
            BASE_FROM + SAP_DONE_WHERE +
            "GROUP BY NVL(PH.CARTYPE,'미지정') ORDER BY SHIP_CNT DESC";
        out.put("vehicleFreq", safeQueryList(carFreqSql, r[0], r[1]));

        // ── 품목별 출고 빈도 (상위 15) — 출고라인 수/총수량/총중량 ──
        String itemFreqSql =
            "SELECT * FROM ( " +
            "  SELECT SI.SKUKEY AS SKUKEY, MAX(SI.DESC01) AS DESC01, " +
            "    COUNT(*) AS LINE_CNT, ROUND(SUM(SI.QTSHPO),0) AS QTY, " +
            "    ROUND(SUM(" + LINE_KG + ")/1000, 1) AS TON " +
            BASE_FROM + SAP_DONE_WHERE +
            "  GROUP BY SI.SKUKEY ORDER BY LINE_CNT DESC " +
            ") WHERE ROWNUM <= 15";
        out.put("itemFreq", safeQueryList(itemFreqSql, r[0], r[1]));

        // ── 요일별 평균 출고량(톤) — 주간 패턴(디지털 트윈 기반) ──
        //   TO_CHAR(..,'D') : 1=일 … 7=토 (NLS 무관하게 요일 숫자 산출 위해 보정)
        String dowSql =
            "SELECT DOW, ROUND(AVG(TON),1) AS AVG_TON, COUNT(*) AS DAYS FROM ( " +
            "  SELECT SH.RQSHPD AS RQSHPD, " +
            "    TO_CHAR(TO_DATE(SH.RQSHPD,'YYYYMMDD'),'D') AS DOW, " +
            "    SUM(" + LINE_KG + ")/1000 AS TON " +
            BASE_FROM + SAP_DONE_WHERE +
            "  GROUP BY SH.RQSHPD, TO_CHAR(TO_DATE(SH.RQSHPD,'YYYYMMDD'),'D') " +
            ") GROUP BY DOW ORDER BY DOW";
        out.put("dowAvg", safeQueryList(dowSql, r[0], r[1]));

        // ── 일자별 출고량(톤) 시계열 → 이동평균 예측 ──
        String dailySql =
            "SELECT SH.RQSHPD AS RQSHPD, ROUND(SUM(" + LINE_KG + ")/1000,1) AS TON " +
            BASE_FROM + SAP_DONE_WHERE +
            "GROUP BY SH.RQSHPD ORDER BY SH.RQSHPD";
        List<Map<String, Object>> daily = normalizeDaily(safeQueryList(dailySql, r[0], r[1]), r[0], r[1]);
        out.put("daily", daily);
        out.put("forecast", buildForecast(daily, 7));   // 향후 7일 예측

        return out;
    }

    /**
     * 간단한 이동평균(최근 7일) 기반 다음 N일 출고량 예측.
     * 운영 환경에서 실데이터가 쌓이면 이동평균으로 추세를 반영한다.
     */
    private List<Map<String, Object>> buildForecast(List<Map<String, Object>> daily, int days) {
        List<Map<String, Object>> fc = new ArrayList<>();
        if (daily == null || daily.isEmpty()) return fc;

        int window = Math.min(7, daily.size());
        double sum = 0.0;
        for (int i = daily.size() - window; i < daily.size(); i++) {
            sum += toDouble(daily.get(i).get("TON"));
        }
        double movAvg = window > 0 ? sum / window : 0.0;

        // 최근 추세(선형 기울기) 소폭 반영
        double slope = 0.0;
        if (daily.size() >= 2) {
            double first = toDouble(daily.get(Math.max(0, daily.size() - window)).get("TON"));
            double last  = toDouble(daily.get(daily.size() - 1).get("TON"));
            slope = (last - first) / Math.max(1, window - 1);
        }

        String lastDate = String.valueOf(daily.get(daily.size() - 1).get("RQSHPD"));
        LocalDate cur;
        try { cur = LocalDate.parse(lastDate, FMT); }
        catch (Exception e) { cur = LocalDate.now(); }

        for (int i = 1; i <= days; i++) {
            cur = cur.plusDays(1);
            double pred = movAvg + slope * i * 0.5;      // 추세 반영(과도 외삽 방지 계수 0.5)
            if (pred < 0) pred = 0;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("RQSHPD", cur.format(FMT));
            m.put("TON",    Math.round(pred * 10.0) / 10.0);
            m.put("FORECAST", true);
            fc.add(m);
        }
        return fc;
    }

    // ══════════════════════════════════════════════════════════════
    //  유틸
    // ══════════════════════════════════════════════════════════════
    private String[] resolveRange(String from, String to, int defaultDays) {
        String end   = (to   != null && !to.isBlank())   ? to.replaceAll("-", "")
                                                         : LocalDate.now().format(FMT);
        String start = (from != null && !from.isBlank()) ? from.replaceAll("-", "")
                                                         : LocalDate.now().minusDays(defaultDays - 1).format(FMT);
        return new String[]{start, end};
    }

    /** 일자 범위 내 누락 날짜를 TON=0, SHIP_CNT=0 으로 채워 반환. */
    private List<Map<String, Object>> normalizeDaily(List<Map<String, Object>> rows, String from, String to) {
        Map<String, Map<String, Object>> byDate = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) byDate.put(String.valueOf(r.get("RQSHPD")), r);
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate cur, end;
        try { cur = LocalDate.parse(from, FMT); end = LocalDate.parse(to, FMT); }
        catch (Exception e) { return rows; }
        // 너무 긴 범위(>120일)는 그대로 반환(0 채움 비용 방지)
        if (cur.plusDays(120).isBefore(end)) return rows;
        while (!cur.isAfter(end)) {
            String key = cur.format(FMT);
            if (byDate.containsKey(key)) {
                result.add(byDate.get(key));
            } else {
                Map<String, Object> zero = new LinkedHashMap<>();
                zero.put("RQSHPD",   key);
                zero.put("SHIP_CNT", 0);
                zero.put("TON",      0);
                result.add(zero);
            }
            cur = cur.plusDays(1);
        }
        return result;
    }

    private List<Map<String, Object>> safeQueryList(String sql, Object... args) {
        try { return jdbcTemplate.queryForList(sql, args); }
        catch (Exception e) {
            log.warn("[ShipAnalysis] query failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> safeQueryForMap(String sql, Object... args) {
        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, args);
            return list.isEmpty() ? Collections.emptyMap() : list.get(0);
        } catch (Exception e) {
            log.warn("[ShipAnalysis] query(map) failed: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return 0L; }
    }

    private double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return 0.0; }
    }
}
