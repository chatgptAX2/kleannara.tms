package com.company.module.dispatch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * TMS 대시보드 Service
 * Oracle 19C – TMS_PS_DISPATCH_H 기반 운송현황 / 효율성 집계
 */
@Slf4j
@Service
public class DashboardService {

    private final JdbcTemplate jdbcTemplate;

    public DashboardService(@Qualifier("tmsJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * [제품군 필터] 대시보드 PS/HL/전체 구분용 WHERE 조건 생성.
     *  · productGroup = "PS" → 제품군 '판지/제지'(SKUG05='10')
     *  · productGroup = "HL" → 제품군 '생활'(SKUG05='20')
     *  · 그 외("ALL"/null/공백) → 빈 문자열(필터 없음) → 기존 동작 100% 동일.
     *
     *  배차 헤더(TMS_PS_DISPATCH_H)에는 제품군이 없고, 배차 품목(TMS_PS_DISPATCH_D)도
     *  SKUG05 미저장이므로, 원본 출하(TMS_SHPDI.SKUG05)까지 조인하여 EXISTS 로 필터한다.
     *  <alias> 는 각 쿼리의 TMS_PS_DISPATCH_H 테이블 별칭(없으면 빈 문자열, 있으면 "h." 형태 접두).
     *
     *  @param productGroup 제품군 코드(PS/HL/ALL)
     *  @param hAlias       헤더 테이블 별칭 접두("" 또는 "h.")
     *  @return WHERE 에 덧붙일 " AND EXISTS(...)" 조건(필터 없으면 "")
     */
    private String productGroupFilter(String productGroup, String hAlias) {
        String skug05 = resolveSkug05(productGroup);
        if (skug05 == null) return "";   // ALL/미지정 → 필터 없음(기존 동작)
        String hPrefix = (hAlias == null || hAlias.isBlank()) ? "" : hAlias;
        return " AND EXISTS ("
             + "   SELECT 1 FROM KNRAWMS.TMS_PS_DISPATCH_D d "
             + "   JOIN KNRAWMS.TMS_SHPDI si "
             + "     ON si.SHPOKY = d.SHPOKY AND si.SHPOIT = d.SHPOIT "
             + "   WHERE d.DISPATCH_NO = " + hPrefix + "DISPATCH_NO "
             + "     AND TRIM(COALESCE(si.SKUG05,'')) = '" + skug05 + "' "
             + " )";
    }

    /** 제품군 코드 → SKUG05 값. PS='10', HL='20', 그 외 null(필터 없음). */
    private String resolveSkug05(String productGroup) {
        if (productGroup == null) return null;
        String g = productGroup.trim().toUpperCase();
        switch (g) {
            case "PS": case "10": return "10";
            case "HL": case "20": return "20";
            default:              return null;   // ALL/빈값/미지원 → 필터 없음
        }
    }

    // ─────────────────────────────────────────────────────────
    // 1. 운송현황
    // ─────────────────────────────────────────────────────────
    public Map<String, Object> getTransportStatus(String dateFrom, String dateTo, String productGroup) {
        String[] range = resolveRange(dateFrom, dateTo, 7);
        // [제품군 필터] PS/HL 지정 시 TMS_SHPDI.SKUG05 조인 EXISTS 조건. ALL/미지정이면 "".
        String pgf = productGroupFilter(productGroup, "");

        // 1-1. 일자별 배차대기/배차완료 차량대수
        //  ※ 실제 상태 컬럼은 STATUS(DRAFT/CONFIRMED). 삭제는 물리삭제로 처리되어
        //    배차 삭제 시 자동으로 집계에서 제외된다. (기존 STAT_CD 는 미사용 컬럼)
        String dailySql =
            "SELECT RQSHPD,\n" +
            "       SUM(CASE WHEN STATUS = 'DRAFT'     THEN 1 ELSE 0 END) AS PENDING_CNT,\n" +
            "       SUM(CASE WHEN STATUS = 'CONFIRMED' THEN 1 ELSE 0 END) AS CONFIRMED_CNT,\n" +
            "       COUNT(*) AS TOTAL_CNT\n" +
            "FROM KNRAWMS.TMS_PS_DISPATCH_H\n" +
            "WHERE RQSHPD >= ? AND RQSHPD <= ?\n" +
            "  AND NVL(STATUS, 'DRAFT') <> 'CANCELLED'" + pgf + "\n" +
            "GROUP BY RQSHPD\n" +
            "ORDER BY RQSHPD";
        List<Map<String, Object>> dailyRows = jdbcTemplate.queryForList(dailySql, range[0], range[1]);

        // 1-2. 출하유형별 차량대수 및 물량(KG) 분포
        String typeSql =
            "SELECT\n" +
            "    CASE WHEN MATERIAL_TYPE IS NULL OR MATERIAL_TYPE = '' THEN 'OTHER'\n" +
            "         ELSE MATERIAL_TYPE END AS MAT_TYPE,\n" +
            "    COUNT(*) AS CAR_CNT,\n" +
            "    ROUND(SUM(TOTAL_KG) / 1000, 2) AS TOTAL_TON\n" +
            "FROM KNRAWMS.TMS_PS_DISPATCH_H\n" +
            "WHERE RQSHPD >= ? AND RQSHPD <= ?\n" +
            "  AND NVL(STATUS, 'DRAFT') <> 'CANCELLED'" + pgf + "\n" +
            "GROUP BY\n" +
            "    CASE WHEN MATERIAL_TYPE IS NULL OR MATERIAL_TYPE = '' THEN 'OTHER'\n" +
            "         ELSE MATERIAL_TYPE END\n" +
            "ORDER BY CAR_CNT DESC";
        List<Map<String, Object>> typeRows = jdbcTemplate.queryForList(typeSql, range[0], range[1]);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dateFrom",  range[0]);
        result.put("dateTo",    range[1]);
        result.put("dailyData", normalizeDailyRows(dailyRows, range[0], range[1]));
        result.put("typeData",  typeRows);
        return result;
    }

    // ─────────────────────────────────────────────────────────
    // 2. 운송효율성
    // ─────────────────────────────────────────────────────────
    public Map<String, Object> getEfficiency(String dateFrom, String dateTo, String productGroup) {
        String[] range = resolveRange(dateFrom, dateTo, 30);
        // [제품군 필터] 별칭 없는 쿼리용("") 과 h 별칭 쿼리용("h.") 두 가지.
        String pgf  = productGroupFilter(productGroup, "");
        String pgfH = productGroupFilter(productGroup, "h.");

        // 2-1. 차량 적재율 구간 분포
        String loadRateSql =
            "SELECT\n" +
            "    CASE\n" +
            "        WHEN LOAD_KG IS NULL OR LOAD_KG = 0 THEN '데이터없음'\n" +
            "        WHEN TOTAL_KG / LOAD_KG < 0.2  THEN '0~20%'\n" +
            "        WHEN TOTAL_KG / LOAD_KG < 0.4  THEN '20~40%'\n" +
            "        WHEN TOTAL_KG / LOAD_KG < 0.6  THEN '40~60%'\n" +
            "        WHEN TOTAL_KG / LOAD_KG < 0.8  THEN '60~80%'\n" +
            "        ELSE '80~100%'\n" +
            "    END AS RATE_BAND,\n" +
            "    COUNT(*) AS CAR_CNT\n" +
            "FROM KNRAWMS.TMS_PS_DISPATCH_H\n" +
            "WHERE RQSHPD >= ? AND RQSHPD <= ?\n" +
            "  AND NVL(STATUS, 'DRAFT') <> 'CANCELLED'" + pgf + "\n" +
            "GROUP BY\n" +
            "    CASE\n" +
            "        WHEN LOAD_KG IS NULL OR LOAD_KG = 0 THEN '데이터없음'\n" +
            "        WHEN TOTAL_KG / LOAD_KG < 0.2  THEN '0~20%'\n" +
            "        WHEN TOTAL_KG / LOAD_KG < 0.4  THEN '20~40%'\n" +
            "        WHEN TOTAL_KG / LOAD_KG < 0.6  THEN '40~60%'\n" +
            "        WHEN TOTAL_KG / LOAD_KG < 0.8  THEN '60~80%'\n" +
            "        ELSE '80~100%'\n" +
            "    END\n" +
            "ORDER BY MIN(CASE\n" +
            "        WHEN LOAD_KG IS NULL OR LOAD_KG = 0 THEN 99\n" +
            "        WHEN TOTAL_KG / LOAD_KG < 0.2  THEN 1\n" +
            "        WHEN TOTAL_KG / LOAD_KG < 0.4  THEN 2\n" +
            "        WHEN TOTAL_KG / LOAD_KG < 0.6  THEN 3\n" +
            "        WHEN TOTAL_KG / LOAD_KG < 0.8  THEN 4\n" +
            "        ELSE 5 END)";
        List<Map<String, Object>> loadRateRows = jdbcTemplate.queryForList(loadRateSql, range[0], range[1]);

        // 2-2. 권역별 물량 분포 (납품처코드 앞 2자리 = 권역)
        String regionSql =
            "SELECT\n" +
            "    NVL(SUBSTR(h.DPTNKY, 1, 2), '기타') AS REGION,\n" +
            "    COUNT(*) AS CAR_CNT,\n" +
            "    ROUND(SUM(h.TOTAL_KG) / 1000, 2) AS TOTAL_TON\n" +
            "FROM KNRAWMS.TMS_PS_DISPATCH_H h\n" +
            "WHERE h.RQSHPD >= ? AND h.RQSHPD <= ?\n" +
            "  AND NVL(h.STATUS, 'DRAFT') <> 'CANCELLED'" + pgfH + "\n" +
            "GROUP BY NVL(SUBSTR(h.DPTNKY, 1, 2), '기타')\n" +
            "ORDER BY TOTAL_TON DESC\n" +
            "FETCH FIRST 10 ROWS ONLY";
        List<Map<String, Object>> regionRows = jdbcTemplate.queryForList(regionSql, range[0], range[1]);

        // 2-3. 평균 적재효율 (전체/ROLL/BOARD)
        String avgSql =
            "SELECT\n" +
            "    NVL(MATERIAL_TYPE, 'ALL') AS MAT_TYPE,\n" +
            "    COUNT(*) AS CAR_CNT,\n" +
            "    ROUND(AVG(CASE WHEN LOAD_KG > 0 THEN TOTAL_KG / LOAD_KG * 100 ELSE NULL END), 1) AS AVG_RATE,\n" +
            "    ROUND(MAX(CASE WHEN LOAD_KG > 0 THEN TOTAL_KG / LOAD_KG * 100 ELSE NULL END), 1) AS MAX_RATE,\n" +
            "    ROUND(MIN(CASE WHEN LOAD_KG > 0 THEN TOTAL_KG / LOAD_KG * 100 ELSE NULL END), 1) AS MIN_RATE\n" +
            "FROM KNRAWMS.TMS_PS_DISPATCH_H\n" +
            "WHERE RQSHPD >= ? AND RQSHPD <= ?\n" +
            "  AND NVL(STATUS, 'DRAFT') <> 'CANCELLED'" + pgf + "\n" +
            "GROUP BY ROLLUP(MATERIAL_TYPE)\n" +
            "ORDER BY AVG_RATE DESC NULLS LAST";
        List<Map<String, Object>> avgRows = jdbcTemplate.queryForList(avgSql, range[0], range[1]);

        // 2-4. 차종별 평균 적재율
        String cartypeSql =
            "SELECT\n" +
            "    CARTYPE,\n" +
            "    COUNT(*) AS CAR_CNT,\n" +
            "    ROUND(AVG(CASE WHEN LOAD_KG > 0 THEN TOTAL_KG / LOAD_KG * 100 ELSE NULL END), 1) AS AVG_RATE\n" +
            "FROM KNRAWMS.TMS_PS_DISPATCH_H\n" +
            "WHERE RQSHPD >= ? AND RQSHPD <= ?\n" +
            "  AND NVL(STATUS, 'DRAFT') <> 'CANCELLED'\n" +
            "  AND CARTYPE IS NOT NULL" + pgf + "\n" +
            "GROUP BY CARTYPE\n" +
            "ORDER BY CAR_CNT DESC\n" +
            "FETCH FIRST 8 ROWS ONLY";
        List<Map<String, Object>> cartypeRows = jdbcTemplate.queryForList(cartypeSql, range[0], range[1]);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dateFrom",    range[0]);
        result.put("dateTo",      range[1]);
        result.put("loadRateData", loadRateRows);
        result.put("regionData",   regionRows);
        result.put("avgRateData",  avgRows);
        result.put("cartypeData",  cartypeRows);
        return result;
    }

    // ─────────────────────────────────────────────────────────
    // 3. 대시보드 KPI 요약
    // ─────────────────────────────────────────────────────────
    public Map<String, Object> getSummary(String productGroup) {
        String today     = LocalDate.now().format(FMT);
        String yesterday = LocalDate.now().minusDays(1).format(FMT);
        String weekStart = LocalDate.now().minusDays(6).format(FMT);
        // [제품군 필터] PS/HL 지정 시 SKUG05 조인 EXISTS 조건. ALL/미지정이면 "".
        String pgf = productGroupFilter(productGroup, "");

        // 오늘 현황
        //  ※ 상태 컬럼은 STATUS(DRAFT/CONFIRMED). 배차대기=DRAFT, 배차완료=CONFIRMED.
        //    삭제는 물리삭제이므로 COUNT/SUM 에서 자동 제외되어
        //    배차 삭제 시 오늘 총 물량/배차대기 건수가 즉시 감산된다.
        String todaySql =
            "SELECT\n" +
            "    SUM(CASE WHEN STATUS = 'DRAFT'     THEN 1 ELSE 0 END) AS PENDING,\n" +
            "    SUM(CASE WHEN STATUS = 'CONFIRMED' THEN 1 ELSE 0 END) AS CONFIRMED,\n" +
            "    SUM(CASE WHEN STATUS = 'CANCELLED' THEN 1 ELSE 0 END) AS CANCELLED,\n" +
            "    COUNT(*) AS TOTAL,\n" +
            "    ROUND(SUM(TOTAL_KG)/1000, 2) AS TOTAL_TON\n" +
            "FROM KNRAWMS.TMS_PS_DISPATCH_H\n" +
            "WHERE RQSHPD = ?\n" +
            "  AND NVL(STATUS, 'DRAFT') <> 'CANCELLED'" + pgf;
        Map<String, Object> todayRow = safeQueryForMap(todaySql, today);

        // 전일 건수 (증감 비교)
        String yestSql = "SELECT COUNT(*) AS TOTAL FROM KNRAWMS.TMS_PS_DISPATCH_H "
                       + "WHERE RQSHPD = ? AND NVL(STATUS, 'DRAFT') <> 'CANCELLED'" + pgf;
        Map<String, Object> yestRow = safeQueryForMap(yestSql, yesterday);

        // 이번주 합계
        String weekSql =
            "SELECT COUNT(*) AS TOTAL_CAR,\n" +
            "       ROUND(SUM(TOTAL_KG)/1000, 2) AS TOTAL_TON,\n" +
            "       ROUND(AVG(CASE WHEN LOAD_KG > 0 THEN TOTAL_KG/LOAD_KG*100 ELSE NULL END), 1) AS AVG_LOAD_RATE\n" +
            "FROM KNRAWMS.TMS_PS_DISPATCH_H\n" +
            "WHERE RQSHPD >= ? AND RQSHPD <= ?\n" +
            "  AND NVL(STATUS, 'DRAFT') <> 'CANCELLED'" + pgf;
        Map<String, Object> weekRow = safeQueryForMap(weekSql, weekStart, today);

        long todayTotal = toLong(todayRow.get("TOTAL"));
        long yestTotal  = toLong(yestRow.get("TOTAL"));
        double growthRate = yestTotal > 0
                ? Math.round((todayTotal - yestTotal) * 1000.0 / yestTotal) / 10.0
                : 0.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today",       today);
        result.put("todayPending",   toLong(todayRow.get("PENDING")));
        result.put("todayConfirmed", toLong(todayRow.get("CONFIRMED")));
        result.put("todayCancelled", toLong(todayRow.get("CANCELLED")));
        result.put("todayTotal",     todayTotal);
        result.put("todayTon",       todayRow.getOrDefault("TOTAL_TON", 0));
        result.put("yesterdayTotal", yestTotal);
        result.put("growthRate",     growthRate);
        result.put("weekTotalCar",   toLong(weekRow.get("TOTAL_CAR")));
        result.put("weekTotalTon",   weekRow.getOrDefault("TOTAL_TON", 0));
        result.put("weekAvgLoadRate",weekRow.getOrDefault("AVG_LOAD_RATE", 0));
        return result;
    }

    // ─────────────────────────────────────────────────────────
    // 유틸
    // ─────────────────────────────────────────────────────────
    private String[] resolveRange(String from, String to, int defaultDays) {
        String end   = (to   != null && !to.isBlank())   ? to.replaceAll("-","")
                                                         : LocalDate.now().format(FMT);
        String start = (from != null && !from.isBlank()) ? from.replaceAll("-","")
                                                         : LocalDate.now().minusDays(defaultDays - 1).format(FMT);
        return new String[]{start, end};
    }

    /** 일자 범위 내 누락된 날짜를 0으로 채워 반환 */
    private List<Map<String, Object>> normalizeDailyRows(
            List<Map<String, Object>> rows, String from, String to) {
        Map<String, Map<String, Object>> byDate = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String d = String.valueOf(r.get("RQSHPD"));
            byDate.put(d, r);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate cur = LocalDate.parse(from, FMT);
        LocalDate end = LocalDate.parse(to,   FMT);
        while (!cur.isAfter(end)) {
            String key = cur.format(FMT);
            if (byDate.containsKey(key)) {
                result.add(byDate.get(key));
            } else {
                Map<String, Object> zero = new LinkedHashMap<>();
                zero.put("RQSHPD",        key);
                zero.put("PENDING_CNT",   0);
                zero.put("CONFIRMED_CNT", 0);
                zero.put("TOTAL_CNT",     0);
                result.add(zero);
            }
            cur = cur.plusDays(1);
        }
        return result;
    }

    private Map<String, Object> safeQueryForMap(String sql, Object... args) {
        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, args);
            return list.isEmpty() ? Collections.emptyMap() : list.get(0);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return 0L; }
    }
}
