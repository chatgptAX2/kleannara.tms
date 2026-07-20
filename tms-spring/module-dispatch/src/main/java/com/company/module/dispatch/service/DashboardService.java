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
 * Oracle 19C – PS_DISPATCH_H 기반 운송현황 / 효율성 집계
 */
@Slf4j
@Service
public class DashboardService {

    private final JdbcTemplate jdbcTemplate;

    public DashboardService(@Qualifier("tmsJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ─────────────────────────────────────────────────────────
    // 1. 운송현황
    // ─────────────────────────────────────────────────────────
    public Map<String, Object> getTransportStatus(String dateFrom, String dateTo) {
        String[] range = resolveRange(dateFrom, dateTo, 7);

        // 1-1. 일자별 배차대기/배차완료 차량대수
        String dailySql = """
            SELECT RQSHPD,
                   SUM(CASE WHEN STAT_CD = 'PENDING'   THEN 1 ELSE 0 END) AS PENDING_CNT,
                   SUM(CASE WHEN STAT_CD = 'CONFIRMED' THEN 1 ELSE 0 END) AS CONFIRMED_CNT,
                   COUNT(*) AS TOTAL_CNT
            FROM KNRAWMS.PS_DISPATCH_H
            WHERE RQSHPD >= ? AND RQSHPD <= ?
            GROUP BY RQSHPD
            ORDER BY RQSHPD
            """;
        List<Map<String, Object>> dailyRows = jdbcTemplate.queryForList(dailySql, range[0], range[1]);

        // 1-2. 출하유형별 차량대수 및 물량(KG) 분포
        String typeSql = """
            SELECT
                CASE WHEN MATERIAL_TYPE IS NULL OR MATERIAL_TYPE = '' THEN 'OTHER'
                     ELSE MATERIAL_TYPE END AS MAT_TYPE,
                COUNT(*) AS CAR_CNT,
                ROUND(SUM(TOTAL_KG) / 1000, 2) AS TOTAL_TON
            FROM KNRAWMS.PS_DISPATCH_H
            WHERE RQSHPD >= ? AND RQSHPD <= ?
            GROUP BY
                CASE WHEN MATERIAL_TYPE IS NULL OR MATERIAL_TYPE = '' THEN 'OTHER'
                     ELSE MATERIAL_TYPE END
            ORDER BY CAR_CNT DESC
            """;
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
    public Map<String, Object> getEfficiency(String dateFrom, String dateTo) {
        String[] range = resolveRange(dateFrom, dateTo, 30);

        // 2-1. 차량 적재율 구간 분포
        String loadRateSql = """
            SELECT
                CASE
                    WHEN LOAD_KG IS NULL OR LOAD_KG = 0 THEN '데이터없음'
                    WHEN TOTAL_KG / LOAD_KG < 0.2  THEN '0~20%'
                    WHEN TOTAL_KG / LOAD_KG < 0.4  THEN '20~40%'
                    WHEN TOTAL_KG / LOAD_KG < 0.6  THEN '40~60%'
                    WHEN TOTAL_KG / LOAD_KG < 0.8  THEN '60~80%'
                    ELSE '80~100%'
                END AS RATE_BAND,
                COUNT(*) AS CAR_CNT
            FROM KNRAWMS.PS_DISPATCH_H
            WHERE RQSHPD >= ? AND RQSHPD <= ?
              AND STAT_CD <> 'CANCELLED'
            GROUP BY
                CASE
                    WHEN LOAD_KG IS NULL OR LOAD_KG = 0 THEN '데이터없음'
                    WHEN TOTAL_KG / LOAD_KG < 0.2  THEN '0~20%'
                    WHEN TOTAL_KG / LOAD_KG < 0.4  THEN '20~40%'
                    WHEN TOTAL_KG / LOAD_KG < 0.6  THEN '40~60%'
                    WHEN TOTAL_KG / LOAD_KG < 0.8  THEN '60~80%'
                    ELSE '80~100%'
                END
            ORDER BY MIN(CASE
                    WHEN LOAD_KG IS NULL OR LOAD_KG = 0 THEN 99
                    WHEN TOTAL_KG / LOAD_KG < 0.2  THEN 1
                    WHEN TOTAL_KG / LOAD_KG < 0.4  THEN 2
                    WHEN TOTAL_KG / LOAD_KG < 0.6  THEN 3
                    WHEN TOTAL_KG / LOAD_KG < 0.8  THEN 4
                    ELSE 5 END)
            """;
        List<Map<String, Object>> loadRateRows = jdbcTemplate.queryForList(loadRateSql, range[0], range[1]);

        // 2-2. 권역별 물량 분포 (납품처코드 앞 2자리 = 권역)
        String regionSql = """
            SELECT
                NVL(SUBSTR(h.DPTNKY, 1, 2), '기타') AS REGION,
                COUNT(*) AS CAR_CNT,
                ROUND(SUM(h.TOTAL_KG) / 1000, 2) AS TOTAL_TON
            FROM KNRAWMS.PS_DISPATCH_H h
            WHERE h.RQSHPD >= ? AND h.RQSHPD <= ?
              AND h.STAT_CD <> 'CANCELLED'
            GROUP BY NVL(SUBSTR(h.DPTNKY, 1, 2), '기타')
            ORDER BY TOTAL_TON DESC
            FETCH FIRST 10 ROWS ONLY
            """;
        List<Map<String, Object>> regionRows = jdbcTemplate.queryForList(regionSql, range[0], range[1]);

        // 2-3. 평균 적재효율 (전체/ROLL/BOARD)
        String avgSql = """
            SELECT
                NVL(MATERIAL_TYPE, 'ALL') AS MAT_TYPE,
                COUNT(*) AS CAR_CNT,
                ROUND(AVG(CASE WHEN LOAD_KG > 0 THEN TOTAL_KG / LOAD_KG * 100 ELSE NULL END), 1) AS AVG_RATE,
                ROUND(MAX(CASE WHEN LOAD_KG > 0 THEN TOTAL_KG / LOAD_KG * 100 ELSE NULL END), 1) AS MAX_RATE,
                ROUND(MIN(CASE WHEN LOAD_KG > 0 THEN TOTAL_KG / LOAD_KG * 100 ELSE NULL END), 1) AS MIN_RATE
            FROM KNRAWMS.PS_DISPATCH_H
            WHERE RQSHPD >= ? AND RQSHPD <= ?
              AND STAT_CD <> 'CANCELLED'
            GROUP BY ROLLUP(MATERIAL_TYPE)
            ORDER BY AVG_RATE DESC NULLS LAST
            """;
        List<Map<String, Object>> avgRows = jdbcTemplate.queryForList(avgSql, range[0], range[1]);

        // 2-4. 차종별 평균 적재율
        String cartypeSql = """
            SELECT
                CARTYPE,
                COUNT(*) AS CAR_CNT,
                ROUND(AVG(CASE WHEN LOAD_KG > 0 THEN TOTAL_KG / LOAD_KG * 100 ELSE NULL END), 1) AS AVG_RATE
            FROM KNRAWMS.PS_DISPATCH_H
            WHERE RQSHPD >= ? AND RQSHPD <= ?
              AND STAT_CD <> 'CANCELLED'
              AND CARTYPE IS NOT NULL
            GROUP BY CARTYPE
            ORDER BY CAR_CNT DESC
            FETCH FIRST 8 ROWS ONLY
            """;
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
    public Map<String, Object> getSummary() {
        String today     = LocalDate.now().format(FMT);
        String yesterday = LocalDate.now().minusDays(1).format(FMT);
        String weekStart = LocalDate.now().minusDays(6).format(FMT);

        // 오늘 현황
        String todaySql = """
            SELECT
                SUM(CASE WHEN STAT_CD = 'PENDING'   THEN 1 ELSE 0 END) AS PENDING,
                SUM(CASE WHEN STAT_CD = 'CONFIRMED' THEN 1 ELSE 0 END) AS CONFIRMED,
                SUM(CASE WHEN STAT_CD = 'CANCELLED' THEN 1 ELSE 0 END) AS CANCELLED,
                COUNT(*) AS TOTAL,
                ROUND(SUM(TOTAL_KG)/1000, 2) AS TOTAL_TON
            FROM KNRAWMS.PS_DISPATCH_H
            WHERE RQSHPD = ?
            """;
        Map<String, Object> todayRow = safeQueryForMap(todaySql, today);

        // 전일 건수 (증감 비교)
        String yestSql = "SELECT COUNT(*) AS TOTAL FROM KNRAWMS.PS_DISPATCH_H WHERE RQSHPD = ?";
        Map<String, Object> yestRow = safeQueryForMap(yestSql, yesterday);

        // 이번주 합계
        String weekSql = """
            SELECT COUNT(*) AS TOTAL_CAR,
                   ROUND(SUM(TOTAL_KG)/1000, 2) AS TOTAL_TON,
                   ROUND(AVG(CASE WHEN LOAD_KG > 0 THEN TOTAL_KG/LOAD_KG*100 ELSE NULL END), 1) AS AVG_LOAD_RATE
            FROM KNRAWMS.PS_DISPATCH_H
            WHERE RQSHPD >= ? AND RQSHPD <= ?
              AND STAT_CD <> 'CANCELLED'
            """;
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
