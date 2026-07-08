package com.company.module.wms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 배차전략 서비스 (DS_INCH12, DS_INCH3, DS_VEHICLE, CMCDV 기반)
 * Flask: /api/dispatch/strategy, /api/dispatch/simulate
 *        /api/carclass, /api/carclass-by-product, /api/ds-vehicle, /api/carclass/save
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyService {

    private final JdbcTemplate jdbc;
    private static final DateTimeFormatter YMDFORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ── 배차전략 조회 (DS_INCH12 + DS_INCH3 + DS_VEHICLE) ─────────
    public Map<String, Object> getStrategy() {
        try {
            List<Map<String, Object>> vehicles = jdbc.queryForList(
                "SELECT * FROM DS_VEHICLE ORDER BY SORT_SEQ"
            );
            List<Map<String, Object>> inch12 = jdbc.queryForList(
                "SELECT * FROM DS_INCH12 ORDER BY GRM, CARTYPE"
            );
            List<Map<String, Object>> inch3 = jdbc.queryForList(
                "SELECT * FROM DS_INCH3 ORDER BY GRM, CARTYPE"
            );
            return Map.of(
                "ok", true,
                "vehicles", vehicles,
                "inch12", inch12,
                "inch3", inch3
            );
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 배차전략 저장 ──────────────────────────────────────────────
    @Transactional
    public Map<String, Object> saveStrategy(Map<String, Object> body) {
        String today = LocalDate.now().format(YMDFORMAT);
        try {
            // inch12 저장
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> inch12List = (List<Map<String, Object>>) body.get("inch12");
            if (inch12List != null) {
                for (Map<String, Object> row : inch12List) {
                    String cartype = (String) row.get("CARTYPE");
                    Object grm = row.get("GRM");
                    Object maxRolls = row.get("MAX_ROLLS");
                    if (cartype == null || grm == null) continue;
                    jdbc.update(
                        "INSERT INTO DS_INCH12 (CARTYPE, GRM, MAX_ROLLS, CREDAT, LMODAT) " +
                        "VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE MAX_ROLLS=?, LMODAT=?",
                        cartype, grm, maxRolls, today, today, maxRolls, today
                    );
                }
            }
            // inch3 저장
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> inch3List = (List<Map<String, Object>>) body.get("inch3");
            if (inch3List != null) {
                for (Map<String, Object> row : inch3List) {
                    String cartype = (String) row.get("CARTYPE");
                    Object grm = row.get("GRM");
                    Object maxRolls = row.get("MAX_ROLLS");
                    if (cartype == null || grm == null) continue;
                    jdbc.update(
                        "INSERT INTO DS_INCH3 (CARTYPE, GRM, MAX_ROLLS, CREDAT, LMODAT) " +
                        "VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE MAX_ROLLS=?, LMODAT=?",
                        cartype, grm, maxRolls, today, today, maxRolls, today
                    );
                }
            }
            return Map.of("ok", true);
        } catch (Exception e) {
            log.error("saveStrategy error: {}", e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 배차 시뮬레이션 (단순 결과 반환) ─────────────────────────
    public Map<String, Object> simulate(Map<String, Object> body) {
        // 시뮬레이션은 ps-dispatch/auto 로직 위임 (복잡한 알고리즘)
        // 여기서는 단순 차량 후보 반환
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
            if (items == null || items.isEmpty())
                return Map.of("ok", false, "error", "items 필수");

            List<Map<String, Object>> vehicles = jdbc.queryForList(
                "SELECT CARTYPE, LOAD_TON, LENGTH_M, WIDTH_M, HEIGHT_M, PALLET_HEIGHT_M, " +
                "SORT_SEQ, PALLET_CNT, LONG_AXIS_YN FROM DS_VEHICLE WHERE USE_YN IS NULL OR USE_YN='Y' " +
                "ORDER BY SORT_SEQ"
            );
            return Map.of("ok", true, "vehicles", vehicles,
                          "message", "시뮬레이션 결과 — 자동배차 API(/api/ps-dispatch/auto) 사용 권장");
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 차종 목록 (CMCDV TMS_CARCLASS10 기반) ─────────────────────
    public Map<String, Object> getCarClass() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT c.CMCDVL AS value, c.CDESC1 AS label, c.USARG1, " +
                "       v.LOAD_TON, v.LENGTH_M, v.WIDTH_M, v.HEIGHT_M " +
                "FROM CMCDV c " +
                "LEFT JOIN DS_VEHICLE v ON v.CARTYPE = c.CDESC1 " +
                "WHERE c.CMCDKY = 'TMS_CARCLASS10' ORDER BY c.CMCDVL"
            );
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 제품(SKU)별 차종 추천 ─────────────────────────────────────
    public Map<String, Object> getCarClassByProduct(String skukey) {
        try {
            // SKU 정보 조회 → 인치/평량 기반 적합 차종 반환
            List<Map<String, Object>> vehicles = jdbc.queryForList(
                "SELECT v.*, " +
                "       COALESCE(i12.MAX_ROLLS, 0) AS MAX_ROLLS_12, " +
                "       COALESCE(i3.MAX_ROLLS, 0)  AS MAX_ROLLS_3 " +
                "FROM DS_VEHICLE v " +
                "LEFT JOIN DS_INCH12 i12 ON i12.CARTYPE=v.CARTYPE AND i12.GRM=60 " +
                "LEFT JOIN DS_INCH3  i3  ON i3.CARTYPE=v.CARTYPE  AND i3.GRM=60 " +
                "ORDER BY v.SORT_SEQ"
            );
            return Map.of("ok", true, "vehicles", vehicles, "skukey", skukey != null ? skukey : "");
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── DS_VEHICLE 전체 목록 ──────────────────────────────────────
    public Map<String, Object> getDsVehicle() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT v.CARCLASS_CD, v.CARTYPE, v.LENGTH_M, v.WIDTH_M, v.HEIGHT_M, " +
                "       v.LOAD_TON, v.PALLET_HEIGHT_M, v.SORT_SEQ, " +
                "       v.PALLET_CNT, v.LONG_AXIS_YN, v.DEFAULT_VEH_CNT, " +
                "       COALESCE(c10.USARG1,'Y') AS USE_YN_PS, " +
                "       COALESCE(c20.USARG1,'Y') AS USE_YN_HL " +
                "FROM DS_VEHICLE v " +
                "LEFT JOIN CMCDV c10 ON c10.CMCDKY='TMS_CARCLASS10' AND c10.CMCDVL=v.CARCLASS_CD " +
                "LEFT JOIN CMCDV c20 ON c20.CMCDKY='TMS_CARCLASS20' AND c20.CMCDVL=v.CARCLASS_CD " +
                "ORDER BY v.SORT_SEQ"
            );
            return Map.of("ok", true, "vehicles", rows);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 차종 저장 (DS_VEHICLE UPSERT) ────────────────────────────
    @Transactional
    public Map<String, Object> saveCarClass(Map<String, Object> body) {
        String today = LocalDate.now().format(YMDFORMAT);
        String carclassCd = (String) body.get("CARCLASS_CD");
        if (carclassCd == null || carclassCd.isBlank())
            return Map.of("ok", false, "error", "CARCLASS_CD 필수");
        try {
            List<Map<String, Object>> exists = jdbc.queryForList(
                "SELECT CARCLASS_CD FROM DS_VEHICLE WHERE CARCLASS_CD=?", carclassCd
            );
            if (exists.isEmpty()) {
                jdbc.update(
                    "INSERT INTO DS_VEHICLE (CARCLASS_CD, CARTYPE, LENGTH_M, WIDTH_M, HEIGHT_M, " +
                    "LOAD_TON, PALLET_HEIGHT_M, SORT_SEQ, PALLET_CNT, LONG_AXIS_YN, DEFAULT_VEH_CNT, CREDAT, LMODAT) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    carclassCd, body.get("CARTYPE"), body.get("LENGTH_M"), body.get("WIDTH_M"),
                    body.get("HEIGHT_M"), body.get("LOAD_TON"), body.get("PALLET_HEIGHT_M"),
                    body.getOrDefault("SORT_SEQ", 0), body.get("PALLET_CNT"),
                    body.getOrDefault("LONG_AXIS_YN", "N"), body.getOrDefault("DEFAULT_VEH_CNT", 1),
                    today, today
                );
            } else {
                jdbc.update(
                    "UPDATE DS_VEHICLE SET CARTYPE=?, LENGTH_M=?, WIDTH_M=?, HEIGHT_M=?, " +
                    "LOAD_TON=?, PALLET_HEIGHT_M=?, SORT_SEQ=?, PALLET_CNT=?, LONG_AXIS_YN=?, " +
                    "DEFAULT_VEH_CNT=?, LMODAT=? WHERE CARCLASS_CD=?",
                    body.get("CARTYPE"), body.get("LENGTH_M"), body.get("WIDTH_M"),
                    body.get("HEIGHT_M"), body.get("LOAD_TON"), body.get("PALLET_HEIGHT_M"),
                    body.getOrDefault("SORT_SEQ", 0), body.get("PALLET_CNT"),
                    body.getOrDefault("LONG_AXIS_YN", "N"), body.getOrDefault("DEFAULT_VEH_CNT", 1),
                    today, carclassCd
                );
            }
            return Map.of("ok", true, "CARCLASS_CD", carclassCd);
        } catch (Exception e) {
            log.error("saveCarClass error: {}", e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        }
    }
}
