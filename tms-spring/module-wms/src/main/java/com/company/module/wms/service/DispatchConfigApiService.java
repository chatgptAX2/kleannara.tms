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
 * 배차설정 API Service
 *
 * ■ DataSource 라우팅
 *   TMS/WMS DataSource 는 동일 Oracle DB (KNMESWMS) / 동일 계정 (KNRATMS).
 *   tmsJdbc 단독으로 BZPTN JOIN BZPTN_DETAIL 직접 수행 가능.
 *
 *   - tmsJdbc: DS_VEHICLE, DS_DISPATCH_PROFILE, DS_DISPATCH_CONST,
 *              DS_DISPATCH_CONST_SET, DS_DISPATCH_CONST_SET_ITEM,
 *              DS_DISPATCH_CONSTRAINT, ROUTE_COST, BZPTN, BZPTN_DETAIL
 *   - wmsJdbc: CMCDV, SHPDH (기존 호출 유지)
 */
@Slf4j
@Service
public class DispatchConfigApiService {

    /** Oracle KNRAWMS 전용 JdbcTemplate */
    private final JdbcTemplate wmsJdbc;
    /** Oracle KNRAWMS tmsJdbc — TMS 테이블 전용 JdbcTemplate */
    private final JdbcTemplate tmsJdbc;

    public DispatchConfigApiService(
            @Qualifier("wmsJdbcTemplate") JdbcTemplate wmsJdbc,
            @Qualifier("tmsJdbcTemplate") JdbcTemplate tmsJdbc) {
        this.wmsJdbc = wmsJdbc;
        this.tmsJdbc = tmsJdbc;
    }

    private static final DateTimeFormatter YMDFORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private String today() { return LocalDate.now().format(YMDFORMAT); }

    // ══════════════════════════════════════════════════════════════
    //  목적식 (DS_DISPATCH_OBJECTIVE) — MariaDB integration
    // ══════════════════════════════════════════════════════════════

    public Map<String, Object> objList() {
        try {
            List<Map<String, Object>> rows = tmsJdbc.queryForList(
                "SELECT * FROM KNRAWMS.DS_DISPATCH_OBJECTIVE ORDER BY SORT_SEQ, OBJ_ID"
            );
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> objSave(Map<String, Object> body) {
        try {
            Long objId   = toLong(body.get("OBJ_ID"));
            String code  = str(body.get("OBJ_CODE"));
            String nm    = str(body.get("OBJ_NM"));
            String icon  = str(body.get("OBJ_ICON"));
            String algo  = str(body.get("OBJ_ALGO"));
            String desc  = str(body.get("OBJ_DESC"));
            int sort     = toInt(body.get("SORT_SEQ"), 0);
            String act   = str(body.getOrDefault("ACTIVE_YN", "Y"));
            if (code.isBlank()) return Map.of("ok", false, "error", "OBJ_CODE 필수");

            if (objId != null) {
                tmsJdbc.update("UPDATE KNRAWMS.DS_DISPATCH_OBJECTIVE SET OBJ_CODE=?,OBJ_NM=?,OBJ_ICON=?,OBJ_ALGO=?,OBJ_DESC=?,SORT_SEQ=?,ACTIVE_YN=?,LMODAT=? WHERE OBJ_ID=?",
                    code, nm, icon, algo, desc, sort, act, today(), objId);
            } else {
                tmsJdbc.update("INSERT INTO KNRAWMS.DS_DISPATCH_OBJECTIVE (OBJ_ID,OBJ_CODE,OBJ_NM,OBJ_ICON,OBJ_ALGO,OBJ_DESC,SORT_SEQ,ACTIVE_YN,CREDAT,LMODAT) VALUES (SEQ_DS_DISPATCH_OBJECTIVE.NEXTVAL,?,?,?,?,?,?,?,?,?)",
                    code, nm, icon, algo, desc, sort, act, today(), today());
                objId = tmsJdbc.queryForObject("SELECT SEQ_DS_DISPATCH_OBJECTIVE.CURRVAL FROM DUAL", Long.class);
            }
            return Map.of("ok", true, "OBJ_ID", objId);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> objDelete(Map<String, Object> body) {
        Long objId = toLong(body.get("OBJ_ID"));
        if (objId == null) return Map.of("ok", false, "error", "OBJ_ID 필수");
        try {
            tmsJdbc.update("DELETE FROM KNRAWMS.DS_DISPATCH_OBJECTIVE WHERE OBJ_ID=?", objId);
            return Map.of("ok", true);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> objActivate(Map<String, Object> body) {
        Long objId = toLong(body.get("OBJ_ID"));
        if (objId == null) return Map.of("ok", false, "error", "OBJ_ID 필수");
        try {
            tmsJdbc.update("UPDATE KNRAWMS.DS_DISPATCH_OBJECTIVE SET ACTIVE_YN='N', LMODAT=?", today());
            tmsJdbc.update("UPDATE KNRAWMS.DS_DISPATCH_OBJECTIVE SET ACTIVE_YN='Y', LMODAT=? WHERE OBJ_ID=?", today(), objId);
            return Map.of("ok", true);
        } catch (Exception e) { return errMap(e); }
    }

    public Map<String, Object> objActive() {
        try {
            // Oracle: FETCH FIRST 1 ROWS ONLY
            List<Map<String, Object>> rows = tmsJdbc.queryForList(
                "SELECT * FROM KNRAWMS.DS_DISPATCH_OBJECTIVE WHERE ACTIVE_YN='Y' ORDER BY OBJ_ID FETCH FIRST 1 ROWS ONLY"
            );
            Map<String, Object> objective = rows.isEmpty() ?
                tmsJdbc.queryForList("SELECT * FROM KNRAWMS.DS_DISPATCH_OBJECTIVE ORDER BY SORT_SEQ, OBJ_ID FETCH FIRST 1 ROWS ONLY")
                    .stream().findFirst().orElse(null) : rows.get(0);

            if (objective == null) return Map.of("ok", false, "error", "목적식 없음");

            String objCode = (String) objective.get("OBJ_CODE");
            List<Map<String, Object>> profiles = tmsJdbc.queryForList(
                "SELECT * FROM KNRAWMS.DS_DISPATCH_PROFILE WHERE OBJECTIVE=? AND ACTIVE_YN='Y' ORDER BY PROFILE_ID FETCH FIRST 1 ROWS ONLY", objCode
            );
            Map<String, Object> profile = profiles.isEmpty() ?
                tmsJdbc.queryForList("SELECT * FROM KNRAWMS.DS_DISPATCH_PROFILE WHERE OBJECTIVE=? ORDER BY PROFILE_ID FETCH FIRST 1 ROWS ONLY", objCode)
                    .stream().findFirst().orElse(null) : profiles.get(0);

            return Map.of("ok", true, "objective", objective, "profile", profile != null ? profile : "");
        } catch (Exception e) { return errMap(e); }
    }

    // ══════════════════════════════════════════════════════════════
    //  제약조건 세트 (DS_DISPATCH_CONST_SET) — MariaDB integration
    // ══════════════════════════════════════════════════════════════

    public Map<String, Object> setList() {
        try {
            List<Map<String, Object>> rows = tmsJdbc.queryForList(
                "SELECT s.*, (SELECT COUNT(*) FROM KNRAWMS.DS_DISPATCH_CONST_SET_ITEM i WHERE i.SET_ID=s.SET_ID) AS ITEM_CNT " +
                "FROM KNRAWMS.DS_DISPATCH_CONST_SET s ORDER BY s.SET_ID"
            );
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> setSave(Map<String, Object> body) {
        try {
            Integer setId = toInteger(body.get("SET_ID"));
            String nm     = str(body.get("SET_NM"));
            String desc   = str(body.get("SET_DESC"));
            String act    = str(body.getOrDefault("ACTIVE_YN", "Y"));
            if (nm.isBlank()) return Map.of("ok", false, "error", "SET_NM 필수");

            if (setId != null) {
                tmsJdbc.update("UPDATE KNRAWMS.DS_DISPATCH_CONST_SET SET SET_NM=?,SET_DESC=?,ACTIVE_YN=?,LMODAT=? WHERE SET_ID=?",
                    nm, desc, act, today(), setId);
            } else {
                tmsJdbc.update("INSERT INTO KNRAWMS.DS_DISPATCH_CONST_SET (SET_ID,SET_NM,SET_DESC,ACTIVE_YN,CREDAT,LMODAT) VALUES (SEQ_DS_DISPATCH_CONST_SET.NEXTVAL,?,?,?,?,?)",
                    nm, desc, act, today(), today());
                setId = tmsJdbc.queryForObject("SELECT SEQ_DS_DISPATCH_CONST_SET.CURRVAL FROM DUAL", Integer.class);
            }
            return Map.of("ok", true, "SET_ID", setId);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> setDelete(Map<String, Object> body) {
        Integer setId = toInteger(body.get("SET_ID"));
        if (setId == null) return Map.of("ok", false, "error", "SET_ID 필수");
        try {
            tmsJdbc.update("DELETE FROM KNRAWMS.DS_DISPATCH_CONST_SET_ITEM WHERE SET_ID=?", setId);
            tmsJdbc.update("DELETE FROM KNRAWMS.DS_DISPATCH_CONST_SET WHERE SET_ID=?", setId);
            tmsJdbc.update("UPDATE KNRAWMS.DS_DISPATCH_PROFILE SET SET_ID=NULL WHERE SET_ID=?", setId);
            return Map.of("ok", true);
        } catch (Exception e) { return errMap(e); }
    }

    public Map<String, Object> setItems(Integer setId) {
        try {
            List<Map<String, Object>> rows = Collections.emptyList();
            if (setId != null) {
                rows = tmsJdbc.queryForList(
                    "SELECT i.ITEM_ID, i.SET_ID, i.CONST_ID, i.ACTIVE_YN, i.PARAM_VALUE, " +
                    "       c.CONST_TYPE, c.CONST_KEY, c.CONST_OP, c.CONST_VALUE, " +
                    "       c.TARGET_ID, c.TARGET_NM, c.NOTE, c.SORT_SEQ " +
                    "FROM KNRAWMS.DS_DISPATCH_CONST_SET_ITEM i " +
                    "JOIN KNRAWMS.DS_DISPATCH_CONST c ON c.CONST_ID=i.CONST_ID " +
                    "WHERE i.SET_ID=? ORDER BY c.CONST_TYPE, c.SORT_SEQ, c.CONST_ID", setId
                );
            }
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) { return errMap(e); }
    }

    public Map<String, Object> setFull(Integer setId) {
        try {
            List<Map<String, Object>> allConsts = tmsJdbc.queryForList(
                "SELECT c.CONST_ID, c.PROFILE_ID, c.CONST_TYPE, c.CONST_KEY, c.CONST_OP, " +
                "       c.CONST_VALUE, c.TARGET_ID, c.TARGET_NM, c.NOTE, c.ACTIVE_YN, c.SORT_SEQ, " +
                "       p.PROFILE_NM " +
                "FROM KNRAWMS.DS_DISPATCH_CONST c " +
                "JOIN KNRAWMS.DS_DISPATCH_PROFILE p ON p.PROFILE_ID=c.PROFILE_ID " +
                "ORDER BY c.CONST_TYPE, c.SORT_SEQ, c.CONST_ID"
            );
            Map<Object, Map<String, Object>> includedMap = new HashMap<>();
            if (setId != null) {
                List<Map<String, Object>> items = tmsJdbc.queryForList(
                    "SELECT CONST_ID, ITEM_ID, ACTIVE_YN, PARAM_VALUE FROM KNRAWMS.DS_DISPATCH_CONST_SET_ITEM WHERE SET_ID=?", setId
                );
                for (Map<String, Object> it : items) includedMap.put(it.get("CONST_ID"), it);
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> c : allConsts) {
                Map<String, Object> d = new LinkedHashMap<>(c);
                Map<String, Object> itemInfo = includedMap.get(d.get("CONST_ID"));
                d.put("IN_SET",      itemInfo != null ? 1 : 0);
                d.put("ITEM_ID",     itemInfo != null ? itemInfo.get("ITEM_ID") : null);
                d.put("ITEM_ACTIVE", itemInfo != null ? itemInfo.get("ACTIVE_YN") : "N");
                d.put("PARAM_VALUE", itemInfo != null ? itemInfo.get("PARAM_VALUE") : null);
                result.add(d);
            }
            return Map.of("ok", true, "rows", result);
        } catch (Exception e) { return errMap(e); }
    }

    public Map<String, Object> setVehicleTypes() {
        try {
            // DS_VEHICLE: tmsJdbc
            List<Map<String, Object>> vehicles = tmsJdbc.queryForList(
                "SELECT v.CARCLASS_CD, v.CARTYPE, v.LENGTH_M, v.WIDTH_M, v.HEIGHT_M, " +
                "       v.LOAD_TON, v.PALLET_HEIGHT_M, v.SORT_SEQ, " +
                "       v.PALLET_CNT, v.LONG_AXIS_YN, v.DEFAULT_VEH_CNT " +
                "FROM KNRAWMS.DS_VEHICLE v ORDER BY v.SORT_SEQ"
            );
            // CMCDV10: Oracle
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
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> setCartypeSave(Map<String, Object> body) {
        Integer setId = toInteger(body.get("set_id"));
        if (setId == null) return Map.of("ok", false, "error", "set_id 필수");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null) items = Collections.emptyList();

        try {
            // CARTYPE 아이템 삭제 후 재삽입
            tmsJdbc.update("DELETE FROM KNRAWMS.DS_DISPATCH_CONST_SET_ITEM WHERE SET_ID=? AND CONST_ID IN " +
                        "(SELECT CONST_ID FROM KNRAWMS.DS_DISPATCH_CONST WHERE CONST_TYPE='CARTYPE')", setId);
            int saved = 0;
            for (Map<String, Object> it : items) {
                if (!"Y".equals(it.get("active_yn"))) continue;
                String carclassCd = str(it.get("carclass_cd"));
                String cartype    = str(it.get("cartype"));
                String field      = str(it.get("field"));
                Object paramVal   = it.get("param_value");
                if (carclassCd.isBlank() || field.isBlank()) continue;

                // DS_DISPATCH_CONST 조회 또는 생성 (MariaDB)
                List<Map<String, Object>> existing = tmsJdbc.queryForList(
                    "SELECT CONST_ID FROM KNRAWMS.DS_DISPATCH_CONST WHERE CONST_TYPE='CARTYPE' AND CONST_KEY=? AND TARGET_ID=?",
                    field, carclassCd
                );
                Long constId;
                if (!existing.isEmpty()) {
                    constId = toLong(existing.get(0).get("CONST_ID"));
                } else {
                    // DS_VEHICLE에서 기본값 (MariaDB)
                    List<Map<String, Object>> vr = tmsJdbc.queryForList(
                        "SELECT * FROM KNRAWMS.DS_VEHICLE WHERE CARCLASS_CD=?", carclassCd
                    );
                    String defaultVal = vr.isEmpty() ? null : Objects.toString(vr.get(0).get(field), null);
                    // 첫 번째 프로파일 ID (MariaDB)
                    List<Map<String, Object>> pr = tmsJdbc.queryForList(
                        "SELECT PROFILE_ID FROM KNRAWMS.DS_DISPATCH_PROFILE ORDER BY PROFILE_ID FETCH FIRST 1 ROWS ONLY"
                    );
                    Long profileId = pr.isEmpty() ? 1L : toLong(pr.get(0).get("PROFILE_ID"));
                    String constOp = List.of("ALLOW_CARTYPE","LONG_AXIS_YN").contains(field) ? "=" : "<=";
                    tmsJdbc.update("INSERT INTO KNRAWMS.DS_DISPATCH_CONST (CONST_ID,PROFILE_ID,CONST_TYPE,CONST_KEY,CONST_VALUE,CONST_OP,TARGET_ID,TARGET_NM,ACTIVE_YN,NOTE,SORT_SEQ,CREDAT,LMODAT) VALUES (SEQ_DS_DISPATCH_CONST.NEXTVAL,?,?,?,?,?,?,?,?,?,?,?,?)",
                        profileId, "CARTYPE", field, defaultVal, constOp, carclassCd, cartype, "Y", "차량유형관리 연동", 0, today(), today());
                    constId = tmsJdbc.queryForObject("SELECT SEQ_DS_DISPATCH_CONST.CURRVAL FROM DUAL", Long.class);
                }
                tmsJdbc.update("INSERT INTO KNRAWMS.DS_DISPATCH_CONST_SET_ITEM (SET_ID,CONST_ID,ACTIVE_YN,PARAM_VALUE) VALUES (?,?,?,?)",
                    setId, constId, "Y", paramVal);
                saved++;
            }
            return Map.of("ok", true, "saved", saved);
        } catch (Exception e) { return errMap(e); }
    }

    public Map<String, Object> setRegionList() {
        try {
            // CMCDV: wmsJdbc (기존 유지)
            List<Map<String, Object>> tmsRegions = wmsJdbc.queryForList(
                "SELECT CMCDVL, CDESC1, CDESC2, USARG3, USARG4 FROM KNRAWMS.CMCDV WHERE CMCDKY='TMS_REGION' ORDER BY CMCDVL"
            );
            // SHPDH JOIN BZPTN JOIN BZPTN_DETAIL — tmsJdbc 단독 (동일 DB이므로 JOIN 가능)
            // REGION_YN은 BZPTN_DETAIL에 없음 → '' 리터럴로 대체 (TMS 측 관리 예정)
            List<Map<String, Object>> partners = tmsJdbc.queryForList(
                "SELECT DISTINCT h.DPTNKY AS PTNRKY, COALESCE(b.NAME01,h.DPTNKY) AS NAME01, " +
                "       COALESCE(b.POSTCD,'') AS POSTCD, COALESCE(d.AREA_CD,'') AS AREA_CD, " +
                "       '' AS REGION_YN " +
                "FROM KNRAWMS.SHPDH h LEFT JOIN KNRAWMS.BZPTN b ON b.PTNRKY=h.DPTNKY AND b.PTNRTY='CT' " +
                "LEFT JOIN KNRAWMS.BZPTN_DETAIL d ON d.PTNRKY=h.DPTNKY AND d.PTNRTY='CT' " +
                "WHERE h.DPTNKY IS NOT NULL AND TRIM(h.DPTNKY) <> '' ORDER BY h.DPTNKY"
            );
            // Python 로직: 우편번호 범위 매핑
            Map<String, Map<String, Object>> regionMap = new LinkedHashMap<>();
            List<Map<String, Object>> unmatched = new ArrayList<>();
            for (Map<String, Object> p : partners) {
                String postcd = Objects.toString(p.get("POSTCD"), "").trim();
                Map<String, Object> matched = null;
                if (!postcd.isEmpty()) {
                    for (Map<String, Object> reg : tmsRegions) {
                        String pf = Objects.toString(reg.get("USARG3"), "").trim();
                        String pt = Objects.toString(reg.get("USARG4"), "").trim();
                        if (!pf.isEmpty() && !pt.isEmpty() && pf.compareTo(postcd) <= 0 && postcd.compareTo(pt) <= 0) {
                            matched = reg; break;
                        }
                    }
                }
                if (matched != null) {
                    final Map<String, Object> finalMatched = matched;
                    String key = (String) finalMatched.get("CMCDVL");
                    regionMap.computeIfAbsent(key, k -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("cmcdvl", k);
                        m.put("region_nm", finalMatched.get("CDESC1"));
                        m.put("sido", finalMatched.get("CDESC2"));
                        m.put("postcd_from", finalMatched.get("USARG3"));
                        m.put("postcd_to", finalMatched.get("USARG4"));
                        m.put("partners", new ArrayList<>());
                        return m;
                    });
                    ((List<Object>) regionMap.get(key).get("partners")).add(p);
                } else {
                    unmatched.add(p);
                }
            }
            return Map.of("ok", true, "regions", new ArrayList<>(regionMap.values()), "unmatched", unmatched);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> setRegionSave(Map<String, Object> body) {
        // REGION_YN 컬럼이 Oracle KNRAWMS.BZPTN_DETAIL에 미존재 — 저장 비지원
        // (조회는 '' 리터럴로 정상 응답, 저장은 DB DDL 컬럼 추가 후 활성화 예정)
        return Map.of("ok", false, "error", "REGION_YN 저장은 현재 지원되지 않습니다. DB 스키마 확인 후 활성화 예정입니다.");
    }

    public Map<String, Object> setEntryTonList() {
        try {
            // tmsJdbc 단독 — BZPTN JOIN BZPTN_DETAIL (동일 DB/계정이므로 JOIN 가능)
            List<Map<String, Object>> partners = tmsJdbc.queryForList(
                "SELECT b.PTNRKY, 'CT' AS PTNRTY, " +
                "       COALESCE(d.OWNRKY,'KN') AS OWNRKY, " +
                "       COALESCE(d.WAREKY,'W001') AS WAREKY, " +
                "       COALESCE(b.NAME01,b.PTNRKY) AS NAME01, " +
                "       d.AREA_CD, d.MAX_TON, d.AUTO_ALLOC_YN " +
                "FROM KNRAWMS.BZPTN b " +
                "LEFT JOIN KNRAWMS.BZPTN_DETAIL d ON d.PTNRKY=b.PTNRKY AND d.PTNRTY=b.PTNRTY AND d.OWNRKY=b.OWNRKY " +
                "WHERE b.PTNRTY='CT' ORDER BY d.AREA_CD, b.PTNRKY"
            );
            List<Map<String, Object>> carclasses = wmsJdbc.queryForList(
                "SELECT CMCDVL AS value, CDESC1 AS label FROM KNRAWMS.CMCDV WHERE CMCDKY='TMS_CARCLASS10' ORDER BY CMCDVL"
            );
            return Map.of("ok", true, "partners", partners, "carclasses", carclasses);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> setEntryTonSave(Map<String, Object> body) {
        return bzptnDetailBatchSave(body, "MAX_TON");
    }

    public Map<String, Object> setForkliftList() {
        try {
            // tmsJdbc 단독 — BZPTN JOIN BZPTN_DETAIL (동일 DB/계정이므로 JOIN 가능)
            List<Map<String, Object>> rows = tmsJdbc.queryForList(
                "SELECT b.PTNRKY, 'CT' AS PTNRTY, " +
                "       COALESCE(d.OWNRKY,'KN') AS OWNRKY, " +
                "       COALESCE(d.WAREKY,'W001') AS WAREKY, " +
                "       COALESCE(b.NAME01,b.PTNRKY) AS NAME01, " +
                "       d.AREA_CD, d.FORKLIFT_YN, d.AUTO_ALLOC_YN " +
                "FROM KNRAWMS.BZPTN b " +
                "LEFT JOIN KNRAWMS.BZPTN_DETAIL d ON d.PTNRKY=b.PTNRKY AND d.PTNRTY=b.PTNRTY AND d.OWNRKY=b.OWNRKY " +
                "WHERE b.PTNRTY='CT' ORDER BY d.AREA_CD, b.PTNRKY"
            );
            return Map.of("ok", true, "partners", rows);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> setForkliftSave(Map<String, Object> body) {
        return bzptnDetailBatchSave(body, "FORKLIFT_YN");
    }

    public Map<String, Object> setDynamicList() {
        try {
            // tmsJdbc 단독 — BZPTN JOIN BZPTN_DETAIL (동일 DB/계정이므로 JOIN 가능)
            List<Map<String, Object>> rows = tmsJdbc.queryForList(
                "SELECT b.PTNRKY, 'CT' AS PTNRTY, " +
                "       COALESCE(d.OWNRKY,'KN') AS OWNRKY, " +
                "       COALESCE(d.WAREKY,'W001') AS WAREKY, " +
                "       COALESCE(b.NAME01,b.PTNRKY) AS NAME01, " +
                "       d.AREA_CD, d.DYNAMIC_YN, d.AUTO_ALLOC_YN " +
                "FROM KNRAWMS.BZPTN b " +
                "LEFT JOIN KNRAWMS.BZPTN_DETAIL d ON d.PTNRKY=b.PTNRKY AND d.PTNRTY=b.PTNRTY AND d.OWNRKY=b.OWNRKY " +
                "WHERE b.PTNRTY='CT' ORDER BY d.AREA_CD, b.PTNRKY"
            );
            return Map.of("ok", true, "partners", rows);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> setDynamicSave(Map<String, Object> body) {
        return bzptnDetailBatchSave(body, "DYNAMIC_YN");
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> setItemsSave(Map<String, Object> body) {
        Integer setId = toInteger(body.get("set_id"));
        if (setId == null) return Map.of("ok", false, "error", "set_id 필수");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null) items = Collections.emptyList();
        try {
            tmsJdbc.update("DELETE FROM KNRAWMS.DS_DISPATCH_CONST_SET_ITEM WHERE SET_ID=?", setId);
            for (Map<String, Object> it : items) {
                Long constId = toLong(it.get("const_id"));
                if (constId == null) continue;
                String yn   = Objects.toString(it.get("active_yn"), "Y").trim();
                Object pval = it.get("param_value");
                tmsJdbc.update("INSERT INTO KNRAWMS.DS_DISPATCH_CONST_SET_ITEM (SET_ID,CONST_ID,ACTIVE_YN,PARAM_VALUE) VALUES (?,?,?,?)",
                    setId, constId, yn, pval);
            }
            return Map.of("ok", true, "saved", items.size());
        } catch (Exception e) { return errMap(e); }
    }

    // ══════════════════════════════════════════════════════════════
    //  제약조건 프로파일 (DS_DISPATCH_PROFILE + DS_DISPATCH_CONST) — MariaDB
    // ══════════════════════════════════════════════════════════════

    public Map<String, Object> profiles() {
        try {
            List<Map<String, Object>> rows = tmsJdbc.queryForList(
                "SELECT * FROM KNRAWMS.DS_DISPATCH_PROFILE ORDER BY PROFILE_ID"
            );
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> profileSave(Map<String, Object> body) {
        try {
            Long pid   = toLong(body.get("PROFILE_ID"));
            String nm  = str(body.get("PROFILE_NM"));
            String obj = str(body.getOrDefault("OBJECTIVE", "MIN_VEHICLES"));
            String act = str(body.getOrDefault("ACTIVE_YN", "Y"));
            String note= str(body.get("NOTE"));
            if (nm.isBlank()) return Map.of("ok", false, "error", "PROFILE_NM 필수");

            if (pid != null) {
                tmsJdbc.update("UPDATE KNRAWMS.DS_DISPATCH_PROFILE SET PROFILE_NM=?,OBJECTIVE=?,ACTIVE_YN=?,NOTE=?,LMODAT=? WHERE PROFILE_ID=?",
                    nm, obj, act, note, today(), pid);
            } else {
                tmsJdbc.update("INSERT INTO KNRAWMS.DS_DISPATCH_PROFILE (PROFILE_ID,PROFILE_NM,OBJECTIVE,ACTIVE_YN,NOTE,CREDAT,LMODAT) VALUES (SEQ_DS_DISPATCH_PROFILE.NEXTVAL,?,?,?,?,?,?)",
                    nm, obj, act, note, today(), today());
                pid = tmsJdbc.queryForObject("SELECT SEQ_DS_DISPATCH_PROFILE.CURRVAL FROM DUAL", Long.class);
            }
            return Map.of("ok", true, "PROFILE_ID", pid);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> profileDelete(Map<String, Object> body) {
        Long pid = toLong(body.get("PROFILE_ID"));
        if (pid == null) return Map.of("ok", false, "error", "PROFILE_ID 필수");
        try {
            tmsJdbc.update("DELETE FROM KNRAWMS.DS_DISPATCH_CONST WHERE PROFILE_ID=?", pid);
            tmsJdbc.update("DELETE FROM KNRAWMS.DS_DISPATCH_PROFILE WHERE PROFILE_ID=?", pid);
            return Map.of("ok", true);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> profileLinkSet(Map<String, Object> body) {
        Long profId = toLong(body.get("profile_id"));
        Integer setId = toInteger(body.get("set_id"));
        if (profId == null) return Map.of("ok", false, "error", "profile_id 필수");
        try {
            tmsJdbc.update("UPDATE KNRAWMS.DS_DISPATCH_PROFILE SET SET_ID=?, LMODAT=? WHERE PROFILE_ID=?", setId, today(), profId);
            return Map.of("ok", true);
        } catch (Exception e) { return errMap(e); }
    }

    public Map<String, Object> constraintAll() {
        try {
            List<Map<String, Object>> rows = tmsJdbc.queryForList(
                "SELECT c.*, p.PROFILE_NM FROM KNRAWMS.DS_DISPATCH_CONST c " +
                "JOIN KNRAWMS.DS_DISPATCH_PROFILE p ON p.PROFILE_ID=c.PROFILE_ID " +
                "ORDER BY c.CONST_TYPE, c.SORT_SEQ, c.CONST_ID"
            );
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) { return errMap(e); }
    }

    public Map<String, Object> constraintList(Long profileId) {
        try {
            List<Map<String, Object>> rows;
            if (profileId != null) {
                rows = tmsJdbc.queryForList(
                    "SELECT * FROM KNRAWMS.DS_DISPATCH_CONST WHERE PROFILE_ID=? ORDER BY SORT_SEQ,CONST_ID", profileId
                );
            } else {
                rows = tmsJdbc.queryForList("SELECT * FROM KNRAWMS.DS_DISPATCH_CONST ORDER BY PROFILE_ID,SORT_SEQ,CONST_ID");
            }
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> constraintSave(Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("rows");
            if (rows != null) {
                List<Long> savedIds = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    savedIds.add(saveOneConstraint(row));
                }
                return Map.of("ok", true, "saved", savedIds.size(), "ids", savedIds);
            }
            Long id = saveOneConstraint(body);
            return Map.of("ok", true, "CONST_ID", id);
        } catch (Exception e) { return errMap(e); }
    }

    private Long saveOneConstraint(Map<String, Object> row) {
        Long cid  = toLong(row.get("CONST_ID"));
        Long pid  = toLong(row.get("PROFILE_ID"));
        String type  = str(row.getOrDefault("CONST_TYPE", "GLOBAL"));
        String key   = str(row.get("CONST_KEY"));
        String val   = str(row.get("CONST_VALUE"));
        String op    = str(row.getOrDefault("CONST_OP", "<="));
        String tid   = str(row.get("TARGET_ID"));
        String tnm   = str(row.get("TARGET_NM"));
        String act   = str(row.getOrDefault("ACTIVE_YN", "Y"));
        String note  = str(row.get("NOTE"));
        int sort     = toInt(row.get("SORT_SEQ"), 0);

        if (cid != null) {
            tmsJdbc.update("UPDATE KNRAWMS.DS_DISPATCH_CONST SET PROFILE_ID=?,CONST_TYPE=?,CONST_KEY=?,CONST_VALUE=?,CONST_OP=?,TARGET_ID=?,TARGET_NM=?,ACTIVE_YN=?,NOTE=?,SORT_SEQ=?,LMODAT=? WHERE CONST_ID=?",
                pid, type, key, val, op, tid, tnm, act, note, sort, today(), cid);
            return cid;
        } else {
            tmsJdbc.update("INSERT INTO KNRAWMS.DS_DISPATCH_CONST (CONST_ID,PROFILE_ID,CONST_TYPE,CONST_KEY,CONST_VALUE,CONST_OP,TARGET_ID,TARGET_NM,ACTIVE_YN,NOTE,SORT_SEQ,CREDAT,LMODAT) VALUES (SEQ_DS_DISPATCH_CONST.NEXTVAL,?,?,?,?,?,?,?,?,?,?,?,?)",
                pid, type, key, val, op, tid, tnm, act, note, sort, today(), today());
            return tmsJdbc.queryForObject("SELECT SEQ_DS_DISPATCH_CONST.CURRVAL FROM DUAL", Long.class);
        }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> constraintDelete(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> ids = (List<Object>) body.get("ids");
        if (ids == null || ids.isEmpty()) return Map.of("ok", false, "error", "ids 필수");
        try {
            String ph = String.join(",", Collections.nCopies(ids.size(), "?"));
            tmsJdbc.update("DELETE FROM KNRAWMS.DS_DISPATCH_CONST WHERE CONST_ID IN (" + ph + ")", ids.toArray());
            return Map.of("ok", true);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> constraintCopyProfile(Map<String, Object> body) {
        Long srcPid = toLong(body.get("src_profile_id"));
        String newNm = str(body.get("new_name"));
        if (srcPid == null || newNm.isBlank()) return Map.of("ok", false, "error", "src_profile_id, new_name 필수");
        try {
            List<Map<String, Object>> src = tmsJdbc.queryForList(
                "SELECT * FROM KNRAWMS.DS_DISPATCH_PROFILE WHERE PROFILE_ID=?", srcPid
            );
            if (src.isEmpty()) return Map.of("ok", false, "error", "원본 프로파일 없음");
            Map<String, Object> s = src.get(0);
            tmsJdbc.update("INSERT INTO KNRAWMS.DS_DISPATCH_PROFILE (PROFILE_ID,PROFILE_NM,OBJECTIVE,ACTIVE_YN,NOTE,CREDAT,LMODAT) VALUES (SEQ_DS_DISPATCH_PROFILE.NEXTVAL,?,?,?,?,?,?)",
                newNm, s.get("OBJECTIVE"), "N", "복사본: " + s.get("PROFILE_NM"), today(), today());
            Long newPid = tmsJdbc.queryForObject("SELECT SEQ_DS_DISPATCH_PROFILE.CURRVAL FROM DUAL", Long.class);
            List<Map<String, Object>> srcRows = tmsJdbc.queryForList(
                "SELECT * FROM KNRAWMS.DS_DISPATCH_CONST WHERE PROFILE_ID=?", srcPid
            );
            for (Map<String, Object> r : srcRows) {
                tmsJdbc.update("INSERT INTO KNRAWMS.DS_DISPATCH_CONST (CONST_ID,PROFILE_ID,CONST_TYPE,CONST_KEY,CONST_VALUE,CONST_OP,TARGET_ID,TARGET_NM,ACTIVE_YN,NOTE,SORT_SEQ,CREDAT,LMODAT) VALUES (SEQ_DS_DISPATCH_CONST.NEXTVAL,?,?,?,?,?,?,?,?,?,?,?,?)",
                    newPid, r.get("CONST_TYPE"), r.get("CONST_KEY"), r.get("CONST_VALUE"), r.get("CONST_OP"),
                    r.get("TARGET_ID"), r.get("TARGET_NM"), r.get("ACTIVE_YN"), r.get("NOTE"), r.get("SORT_SEQ"), today(), today());
            }
            return Map.of("ok", true, "new_profile_id", newPid);
        } catch (Exception e) { return errMap(e); }
    }

    public Map<String, Object> constraintMeta() {
        try {
            // DS_VEHICLE: MariaDB
            List<Map<String, Object>> vehicles = tmsJdbc.queryForList(
                "SELECT CARCLASS_CD, CARTYPE, LOAD_TON, LENGTH_M, WIDTH_M, HEIGHT_M, PALLET_HEIGHT_M, SORT_SEQ " +
                "FROM KNRAWMS.DS_VEHICLE ORDER BY SORT_SEQ"
            );
            // CMCDV: Oracle KNRAWMS
            List<Map<String, Object>> carclasses = wmsJdbc.queryForList(
                "SELECT CMCDVL, CDESC1 FROM KNRAWMS.CMCDV WHERE CMCDKY='TMS_CARCLASS10' ORDER BY CMCDVL"
            );
            // ROUTE_COST JOIN BZPTN — tmsJdbc 단독 (동일 DB/계정이므로 JOIN 가능)
            List<Map<String, Object>> partners = tmsJdbc.queryForList(
                "SELECT DISTINCT r.PTNRKY, COALESCE(b.NAME01,r.PTNRKY) AS PTNRNM " +
                "FROM KNRAWMS.ROUTE_COST r " +
                "LEFT JOIN KNRAWMS.BZPTN b ON b.PTNRKY=r.PTNRKY AND b.PTNRTY='CT' " +
                "ORDER BY r.PTNRKY FETCH FIRST 300 ROWS ONLY"
            );

            List<Map<String, Object>> constKeyDefs = buildConstKeyDefs();
            return Map.of("ok", true, "vehicles", vehicles, "carclasses", carclasses,
                          "partners", partners, "const_key_defs", constKeyDefs);
        } catch (Exception e) { return errMap(e); }
    }

    public Map<String, Object> constraintAuto(Map<String, Object> body) {
        return Map.of("ok", false, "error", "제약조건 기반 자동배차는 /api/ps-dispatch/auto를 사용하세요");
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────

    /**
     * BZPTN_DETAIL 배치 저장 — TMS Oracle KNRAWMS → tmsJdbc
     * BZPTN_DETAIL 은 WMS Oracle(wmsJdbc) 소속이 아닌 TMS Oracle(tmsJdbc) 소속.
     * Oracle에서는 ON DUPLICATE KEY UPDATE 미지원 → MERGE INTO 사용
     */
    @Transactional(transactionManager = "tmsTransactionManager")
    private Map<String, Object> bzptnDetailBatchSave(Map<String, Object> body, String columnName) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) return Map.of("ok", true, "saved", 0);
        String lmodat = today();
        String lmotim = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        int saved = 0;
        try {
            for (Map<String, Object> it : items) {
                String ptnrky = str(it.get("ptnrky"));
                String ptnrty = Objects.toString(it.get("ptnrty"), "CT").trim();
                String ownrky = Objects.toString(it.get("ownrky"), "KN").trim();
                String wareky = Objects.toString(it.getOrDefault("wareky", "W001"), "W001").trim();
                Object colVal = it.get(columnName.toLowerCase());
                if (colVal == null) colVal = it.get(columnName);
                if (ptnrky.isBlank()) continue;
                // Oracle MERGE INTO (ON DUPLICATE KEY UPDATE 대체) — tmsJdbc (TMS DB)
                tmsJdbc.update(
                    "MERGE INTO KNRAWMS.BZPTN_DETAIL t " +
                    "USING (SELECT ? AS PTNRKY, ? AS PTNRTY, ? AS OWNRKY, ? AS WAREKY FROM DUAL) s " +
                    "ON (t.PTNRKY=s.PTNRKY AND t.PTNRTY=s.PTNRTY AND t.OWNRKY=s.OWNRKY AND t.WAREKY=s.WAREKY) " +
                    "WHEN MATCHED THEN UPDATE SET t." + columnName + "=?, t.LMODAT=?, t.LMOTIM=?, t.LMOUSR='DCON_SET' " +
                    "WHEN NOT MATCHED THEN INSERT (PTNRKY,PTNRTY,OWNRKY,WAREKY," + columnName + ",LMODAT,LMOTIM,LMOUSR) " +
                    "VALUES (?,?,?,?,?,?,?,'DCON_SET')",
                    ptnrky, ptnrty, ownrky, wareky,
                    colVal, lmodat, lmotim,
                    ptnrky, ptnrty, ownrky, wareky, colVal, lmodat, lmotim
                );
                saved++;
            }
            return Map.of("ok", true, "saved", saved);
        } catch (Exception e) { return errMap(e); }
    }

    private List<Map<String, Object>> buildConstKeyDefs() {
        return Arrays.asList(
            Map.of("type","GLOBAL","key","MAX_VEHICLES_PER_GROUP","label","그룹당 최대 차량 수","op_default","<=","value_type","int"),
            Map.of("type","GLOBAL","key","ALLOW_SPLIT_ITEM","label","납품분할 허용","op_default","=","value_type","yn"),
            Map.of("type","GLOBAL","key","ALLOW_MIXED_LOAD","label","혼적 허용","op_default","=","value_type","yn"),
            Map.of("type","GLOBAL","key","MIN_FILL_RATIO","label","최소 적재율(%)","op_default",">=","value_type","float"),
            Map.of("type","GLOBAL","key","MAX_FILL_RATIO","label","최대 적재율(%)","op_default","<=","value_type","float"),
            Map.of("type","VEHICLE","key","ALLOW_CARTYPE","label","차종 허용 여부","op_default","=","value_type","yn"),
            Map.of("type","VEHICLE","key","MAX_LOAD_RATIO","label","차종별 최대 적재율(%)","op_default","<=","value_type","float"),
            Map.of("type","PARTNER","key","MAX_TON_OVERRIDE","label","납품처 최대 톤수 재정의","op_default","=","value_type","text"),
            Map.of("type","PARTNER","key","FORKLIFT_REQUIRED","label","지게차 필수 여부","op_default","=","value_type","yn"),
            Map.of("type","CARGO","key","MAX_ROLL_STACK_TIER","label","롤 최대 적재 단수","op_default","<=","value_type","int"),
            Map.of("type","CARGO","key","MAX_BOARD_HEIGHT_M","label","판지 최대 적재 높이(m)","op_default","<=","value_type","float"),
            Map.of("type","COST","key","COST_PENALTY_OVER","label","초과 적재 패널티 배수","op_default","=","value_type","float")
        );
    }

    private Map<String, Object> errMap(Exception e) {
        log.error("DispatchConfigApiService error: {}", e.getMessage());
        return Map.of("ok", false, "error", e.getMessage());
    }
    private String str(Object v) { return v == null ? "" : v.toString().trim(); }
    private Long toLong(Object v) { try { return v == null ? null : Long.valueOf(v.toString()); } catch (Exception e) { return null; } }
    private Integer toInteger(Object v) { try { return v == null ? null : Integer.valueOf(v.toString()); } catch (Exception e) { return null; } }
    private int toInt(Object v, int def) { try { return v == null ? def : Integer.parseInt(v.toString()); } catch (Exception e) { return def; } }
}
