package com.company.module.wms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 배차전략 서비스
 *
 * ■ DataSource 라우팅
 *   - wmsJdbc (Oracle KNRAWMS): CMCDV
 *   - tmsJdbc (MariaDB integration): DS_VEHICLE, DS_INCH12, DS_INCH3
 *
 *   ※ Cross-DB 조인(KNRAWMS.CMCDV ↔ DS_VEHICLE) 불가 → 2-step 분리
 */
@Slf4j
@Service
public class StrategyService {

    /** Oracle KNRAWMS 전용 JdbcTemplate */
    private final JdbcTemplate wmsJdbc;
    /** MariaDB integration 전용 JdbcTemplate */
    private final JdbcTemplate tmsJdbc;

    public StrategyService(
            @Qualifier("wmsJdbcTemplate") JdbcTemplate wmsJdbc,
            @Qualifier("tmsJdbcTemplate") JdbcTemplate tmsJdbc) {
        this.wmsJdbc = wmsJdbc;
        this.tmsJdbc = tmsJdbc;
    }

    private static final DateTimeFormatter YMDFORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ── 배차전략 조회 (DS_INCH12 + DS_INCH3 + DS_VEHICLE) — MariaDB ─
    public Map<String, Object> getStrategy() {
        try {
            List<Map<String, Object>> vehicles = tmsJdbc.queryForList(
                "SELECT * FROM DS_VEHICLE ORDER BY SORT_SEQ"
            );
            List<Map<String, Object>> inch12 = tmsJdbc.queryForList(
                "SELECT * FROM DS_INCH12 ORDER BY GRM, CARTYPE"
            );
            List<Map<String, Object>> inch3 = tmsJdbc.queryForList(
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

    // ── 배차전략 저장 — DS_INCH12/DS_INCH3: MariaDB ────────────────
    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> saveStrategy(Map<String, Object> body) {
        String today = LocalDate.now().format(YMDFORMAT);
        try {
            // inch12 저장 (MariaDB: ON DUPLICATE KEY UPDATE 정상 지원)
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> inch12List = (List<Map<String, Object>>) body.get("inch12");
            if (inch12List != null) {
                for (Map<String, Object> row : inch12List) {
                    String cartype = (String) row.get("CARTYPE");
                    Object grm = row.get("GRM");
                    Object maxRolls = row.get("MAX_ROLLS");
                    if (cartype == null || grm == null) continue;
                    tmsJdbc.update(
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
                    tmsJdbc.update(
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
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
            if (items == null || items.isEmpty())
                return Map.of("ok", false, "error", "items 필수");

            // DS_VEHICLE: MariaDB
            List<Map<String, Object>> vehicles = tmsJdbc.queryForList(
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
    // Cross-DB 조인 불가(Oracle CMCDV ↔ MariaDB DS_VEHICLE) → 2-step
    public Map<String, Object> getCarClass() {
        try {
            // Step 1: Oracle KNRAWMS.CMCDV → wmsJdbc
            List<Map<String, Object>> ccRows = wmsJdbc.queryForList(
                "SELECT c.CMCDVL AS value, c.CDESC1 AS label, c.USARG1 " +
                "FROM KNRAWMS.CMCDV c WHERE c.CMCDKY = 'TMS_CARCLASS10' ORDER BY c.CMCDVL"
            );
            // Step 2: MariaDB DS_VEHICLE → tmsJdbc
            List<Map<String, Object>> vehRows = tmsJdbc.queryForList(
                "SELECT CARTYPE, LOAD_TON, LENGTH_M, WIDTH_M, HEIGHT_M FROM DS_VEHICLE"
            );
            Map<String, Map<String, Object>> vehByCartype = new LinkedHashMap<>();
            for (Map<String, Object> v : vehRows) vehByCartype.put(str(v.get("CARTYPE")), v);

            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> cc : ccRows) {
                Map<String, Object> row = new LinkedHashMap<>(cc);
                Map<String, Object> veh = vehByCartype.get(str(cc.get("label")));
                row.put("LOAD_TON", veh != null ? veh.get("LOAD_TON") : null);
                row.put("LENGTH_M", veh != null ? veh.get("LENGTH_M") : null);
                row.put("WIDTH_M",  veh != null ? veh.get("WIDTH_M")  : null);
                row.put("HEIGHT_M", veh != null ? veh.get("HEIGHT_M") : null);
                rows.add(row);
            }
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 제품(SKU)별 차종 추천 — DS_VEHICLE/DS_INCH12/DS_INCH3: MariaDB
    public Map<String, Object> getCarClassByProduct(String skukey) {
        try {
            List<Map<String, Object>> vehicles = tmsJdbc.queryForList(
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

    // ── DS_VEHICLE 전체 목록 — DS_VEHICLE: MariaDB / CMCDV: Oracle → 2-step
    public Map<String, Object> getDsVehicle() {
        try {
            // Step 1: MariaDB DS_VEHICLE
            List<Map<String, Object>> vehicles = tmsJdbc.queryForList(
                "SELECT v.CARCLASS_CD, v.CARTYPE, v.LENGTH_M, v.WIDTH_M, v.HEIGHT_M, " +
                "       v.LOAD_TON, v.PALLET_HEIGHT_M, v.SORT_SEQ, " +
                "       v.PALLET_CNT, v.LONG_AXIS_YN, v.DEFAULT_VEH_CNT " +
                "FROM DS_VEHICLE v ORDER BY v.SORT_SEQ"
            );
            // Step 2: Oracle KNRAWMS.CMCDV
            List<Map<String, Object>> cc10 = wmsJdbc.queryForList(
                "SELECT CMCDVL, USARG1 FROM KNRAWMS.CMCDV WHERE CMCDKY='TMS_CARCLASS10'"
            );
            List<Map<String, Object>> cc20 = wmsJdbc.queryForList(
                "SELECT CMCDVL, USARG1 FROM KNRAWMS.CMCDV WHERE CMCDKY='TMS_CARCLASS20'"
            );
            Map<String, String> useYnPs = new HashMap<>(), useYnHl = new HashMap<>();
            for (Map<String, Object> r : cc10) useYnPs.put(str(r.get("CMCDVL")), str(r.get("USARG1")));
            for (Map<String, Object> r : cc20) useYnHl.put(str(r.get("CMCDVL")), str(r.get("USARG1")));

            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> v : vehicles) {
                String cc = str(v.get("CARCLASS_CD"));
                Map<String, Object> row = new LinkedHashMap<>(v);
                row.put("USE_YN_PS", useYnPs.getOrDefault(cc, "Y"));
                row.put("USE_YN_HL", useYnHl.getOrDefault(cc, "Y"));
                rows.add(row);
            }
            return Map.of("ok", true, "vehicles", rows);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 차종 저장 (DS_VEHICLE UPSERT) — MariaDB ──────────────────
    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> saveCarClass(Map<String, Object> body) {
        String today = LocalDate.now().format(YMDFORMAT);
        String carclassCd = (String) body.get("CARCLASS_CD");
        if (carclassCd == null || carclassCd.isBlank())
            return Map.of("ok", false, "error", "CARCLASS_CD 필수");
        try {
            List<Map<String, Object>> exists = tmsJdbc.queryForList(
                "SELECT CARCLASS_CD FROM DS_VEHICLE WHERE CARCLASS_CD=?", carclassCd
            );
            if (exists.isEmpty()) {
                tmsJdbc.update(
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
                tmsJdbc.update(
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

    private String str(Object v) { return v == null ? "" : v.toString().trim(); }
}
