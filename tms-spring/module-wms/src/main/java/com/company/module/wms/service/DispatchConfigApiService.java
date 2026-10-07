package com.company.module.wms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
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
 *   - tmsJdbc: TMS_DS_VEHICLE, TMS_DS_DISPATCH_PROFILE, TMS_DS_DISPATCH_CONST,
 *              TMS_DS_DISPATCH_CONST_SET, TMS_DS_DISPATCH_CONST_SET_ITEM,
 *              TMS_DS_DISPATCH_CONSTRAINT, TMS_ROUTE_COST, BZPTN, BZPTN_DETAIL
 *   - wmsJdbc: CMCDV, TMS_SHPDH (기존 호출 유지)
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

    // ── [PS/HL 스코프 분리] ─────────────────────────────────────────
    //   배차 스코프: 'PS'(SKUG05='10'/TMS_CARCLASS10) | 'HL'(SKUG05='20'/TMS_CARCLASS20).
    //   4개 테이블(OBJECTIVE/PROFILE/CONST/CONST_SET)에 SCOPE 컬럼 추가.
    //   기존 데이터(SCOPE NULL/공백)는 'PS' 로 취급(하위호환) → 조회는 COALESCE(SCOPE,'PS') 비교.
    private static String normScope(Object scope) {
        String s = scope != null ? scope.toString().trim() : "";
        return "HL".equalsIgnoreCase(s) ? "HL" : "PS";
    }
    // 스코프에 대응하는 차량 제품군 공통코드 (TMS_CARCLASS10=PS, TMS_CARCLASS20=HL)
    private static String carclassKey(String scope) {
        return "HL".equals(scope) ? "TMS_CARCLASS20" : "TMS_CARCLASS10";
    }
    // 스코프에 대응하는 납품처 제품군 코드 (BZPTN.PTNL01: PS='10', HL='20')
    //   PS=판지, HL=생활 납품처 구분 기준. 제약조건관리 납품처 조회/저장 필터에 사용.
    private static String ptnl01Of(String scope) {
        return "HL".equals(scope) ? "20" : "10";
    }
    // 프로파일(PROFILE_ID)의 SCOPE 조회 — CONST 저장 시 스코프 상속용(없으면 'PS').
    private String profileScope(Long profileId) {
        if (profileId == null) return "PS";
        try {
            return normScope(tmsJdbc.queryForList(
                "SELECT COALESCE(SCOPE,'PS') AS SCOPE FROM KNRAWMS.TMS_DS_DISPATCH_PROFILE WHERE PROFILE_ID=?", profileId)
                .stream().findFirst().map(r -> r.get("SCOPE")).orElse("PS"));
        } catch (Exception e) { return "PS"; }
    }

    /**
     * TMS_DS_DISPATCH_CONST.CONST_ID 채번.
     * SEQ_DS_DISPATCH_CONST 시퀀스가 존재하면 NEXTVAL, 없으면(ORA-02289) MAX+1로 폴백.
     * 운영 Oracle KNRAWMS에 시퀀스가 배포되지 않은 환경에서도 안전하게 INSERT 가능.
     */
    private Long nextConstId() {
        try {
            return tmsJdbc.queryForObject("SELECT SEQ_DS_DISPATCH_CONST.NEXTVAL FROM DUAL", Long.class);
        } catch (Exception seqEx) {
            return tmsJdbc.queryForObject(
                "SELECT NVL(MAX(CONST_ID),0)+1 FROM KNRAWMS.TMS_DS_DISPATCH_CONST", Long.class);
        }
    }

    /**
     * TMS_DS_DISPATCH_OBJECTIVE.OBJ_ID 채번.
     * SEQ_DS_DISPATCH_OBJECTIVE 시퀀스가 존재하면 NEXTVAL, 없으면(ORA-02289) MAX+1로 폴백.
     * 운영 Oracle KNRAWMS에 시퀀스 미배포 환경에서도 안전하게 INSERT 가능.
     */
    private Long nextObjId() {
        try {
            return tmsJdbc.queryForObject("SELECT SEQ_DS_DISPATCH_OBJECTIVE.NEXTVAL FROM DUAL", Long.class);
        } catch (Exception seqEx) {
            return tmsJdbc.queryForObject(
                "SELECT NVL(MAX(OBJ_ID),0)+1 FROM KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE", Long.class);
        }
    }

    /**
     * TMS_DS_DISPATCH_PROFILE.PROFILE_ID 채번.
     * SEQ_DS_DISPATCH_PROFILE 시퀀스가 존재하면 NEXTVAL, 없으면(ORA-02289) MAX+1로 폴백.
     */
    private Long nextProfileId() {
        try {
            return tmsJdbc.queryForObject("SELECT SEQ_DS_DISPATCH_PROFILE.NEXTVAL FROM DUAL", Long.class);
        } catch (Exception seqEx) {
            return tmsJdbc.queryForObject(
                "SELECT NVL(MAX(PROFILE_ID),0)+1 FROM KNRAWMS.TMS_DS_DISPATCH_PROFILE", Long.class);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  목적식 (TMS_DS_DISPATCH_OBJECTIVE) — MariaDB integration
    // ══════════════════════════════════════════════════════════════

    public Map<String, Object> objList() { return objList("PS"); }

    public Map<String, Object> objList(String scopeIn) {
        try {
            String scope = normScope(scopeIn);
            List<Map<String, Object>> rows = tmsJdbc.queryForList(
                "SELECT * FROM KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE " +
                "WHERE COALESCE(SCOPE,'PS')=? ORDER BY SORT_SEQ, OBJ_ID", scope
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
            String scope = normScope(body.get("SCOPE"));
            if (code.isBlank()) return Map.of("ok", false, "error", "OBJ_CODE 필수");

            // ── OBJ_CODE 중복 사전 검사(스코프 내에서만) ─────────────────────
            //   유니크 제약 UK_DS_DISPATCH_OBJECTIVE 위배(ORA-00001)를 raw 스택 대신
            //   명확한 한글 메시지로 안내. '같은 스코프' 안에서만 중복으로 판정하므로
            //   PS/HL 이 동일 OBJ_CODE(예: MIN_VEHICLES)를 각각 보유하는 것은 허용된다.
            //   ※ 이 정책이 실제로 저장되려면 운영 제약이 (OBJ_CODE, SCOPE) 복합이어야 함(DDL 필요).
            {
                String dupSql = "SELECT COUNT(*) FROM KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE " +
                                "WHERE OBJ_CODE=? AND COALESCE(SCOPE,'PS')=?" +
                                (objId != null ? " AND OBJ_ID<>?" : "");
                Integer dup = (objId != null)
                    ? tmsJdbc.queryForObject(dupSql, Integer.class, code, scope, objId)
                    : tmsJdbc.queryForObject(dupSql, Integer.class, code, scope);
                if (dup != null && dup > 0) {
                    return Map.of("ok", false, "error",
                        "이미 존재하는 목적식 코드입니다: " + code + " (" + scope + ")");
                }
            }

            if (objId != null) {
                // 수정 시 SCOPE 는 생성 시점 값 유지(변경 안 함).
                tmsJdbc.update("UPDATE KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE SET OBJ_CODE=?,OBJ_NM=?,OBJ_ICON=?,OBJ_ALGO=?,OBJ_DESC=?,SORT_SEQ=?,ACTIVE_YN=?,LMODAT=? WHERE OBJ_ID=?",
                    code, nm, icon, algo, desc, sort, act, today(), objId);
            } else {
                // OBJ_ID 채번: 시퀀스 존재 시 NEXTVAL, 없으면 MAX+1 폴백(ORA-02289 방지).
                objId = nextObjId();
                tmsJdbc.update("INSERT INTO KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE (OBJ_ID,OBJ_CODE,OBJ_NM,OBJ_ICON,OBJ_ALGO,OBJ_DESC,SORT_SEQ,ACTIVE_YN,SCOPE,CREDAT,LMODAT) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    objId, code, nm, icon, algo, desc, sort, act, scope, today(), today());
            }
            return Map.of("ok", true, "OBJ_ID", objId);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> objDelete(Map<String, Object> body) {
        Long objId = toLong(body.get("OBJ_ID"));
        if (objId == null) return Map.of("ok", false, "error", "OBJ_ID 필수");
        try {
            tmsJdbc.update("DELETE FROM KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE WHERE OBJ_ID=?", objId);
            return Map.of("ok", true);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> objActivate(Map<String, Object> body) {
        Long objId = toLong(body.get("OBJ_ID"));
        if (objId == null) return Map.of("ok", false, "error", "OBJ_ID 필수");
        try {
            // 활성 목적식 단일 보장은 '해당 목적식의 스코프' 안에서만 처리(PS/HL 상호 독립).
            String scope = normScope(tmsJdbc.queryForList(
                "SELECT COALESCE(SCOPE,'PS') AS SCOPE FROM KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE WHERE OBJ_ID=?", objId)
                .stream().findFirst().map(r -> r.get("SCOPE")).orElse("PS"));
            tmsJdbc.update("UPDATE KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE SET ACTIVE_YN='N', LMODAT=? WHERE COALESCE(SCOPE,'PS')=?", today(), scope);
            tmsJdbc.update("UPDATE KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE SET ACTIVE_YN='Y', LMODAT=? WHERE OBJ_ID=?", today(), objId);
            return Map.of("ok", true);
        } catch (Exception e) { return errMap(e); }
    }

    public Map<String, Object> objActive() { return objActive("PS"); }

    public Map<String, Object> objActive(String scopeIn) {
        try {
            String scope = normScope(scopeIn);
            // Oracle: FETCH FIRST 1 ROWS ONLY — 스코프 내 활성 목적식
            List<Map<String, Object>> rows = tmsJdbc.queryForList(
                "SELECT * FROM KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE WHERE ACTIVE_YN='Y' AND COALESCE(SCOPE,'PS')=? ORDER BY OBJ_ID FETCH FIRST 1 ROWS ONLY", scope
            );
            Map<String, Object> objective = rows.isEmpty() ?
                tmsJdbc.queryForList("SELECT * FROM KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE WHERE COALESCE(SCOPE,'PS')=? ORDER BY SORT_SEQ, OBJ_ID FETCH FIRST 1 ROWS ONLY", scope)
                    .stream().findFirst().orElse(null) : rows.get(0);

            if (objective == null) return Map.of("ok", false, "error", "목적식 없음");

            String objCode = (String) objective.get("OBJ_CODE");
            // 프로파일도 동일 스코프 내에서 매칭
            List<Map<String, Object>> profiles = tmsJdbc.queryForList(
                "SELECT * FROM KNRAWMS.TMS_DS_DISPATCH_PROFILE WHERE OBJECTIVE=? AND ACTIVE_YN='Y' AND COALESCE(SCOPE,'PS')=? ORDER BY PROFILE_ID FETCH FIRST 1 ROWS ONLY", objCode, scope
            );
            Map<String, Object> profile = profiles.isEmpty() ?
                tmsJdbc.queryForList("SELECT * FROM KNRAWMS.TMS_DS_DISPATCH_PROFILE WHERE OBJECTIVE=? AND COALESCE(SCOPE,'PS')=? ORDER BY PROFILE_ID FETCH FIRST 1 ROWS ONLY", objCode, scope)
                    .stream().findFirst().orElse(null) : profiles.get(0);

            return Map.of("ok", true, "objective", objective, "profile", profile != null ? profile : "");
        } catch (Exception e) { return errMap(e); }
    }

    // ══════════════════════════════════════════════════════════════
    //  제약조건 세트 (TMS_DS_DISPATCH_CONST_SET) — MariaDB integration
    // ══════════════════════════════════════════════════════════════

    public Map<String, Object> setList() { return setList("PS"); }

    public Map<String, Object> setList(String scopeIn) {
        try {
            String scope = normScope(scopeIn);
            List<Map<String, Object>> rows = tmsJdbc.queryForList(
                "SELECT s.*, (SELECT COUNT(*) FROM KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM i WHERE i.SET_ID=s.SET_ID) AS ITEM_CNT " +
                "FROM KNRAWMS.TMS_DS_DISPATCH_CONST_SET s WHERE COALESCE(s.SCOPE,'PS')=? ORDER BY s.SET_ID", scope
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
            String scope  = normScope(body.get("SCOPE"));
            if (nm.isBlank()) return Map.of("ok", false, "error", "SET_NM 필수");

            if (setId != null) {
                // 수정 시 SCOPE 유지
                tmsJdbc.update("UPDATE KNRAWMS.TMS_DS_DISPATCH_CONST_SET SET SET_NM=?,SET_DESC=?,ACTIVE_YN=?,LMODAT=? WHERE SET_ID=?",
                    nm, desc, act, today(), setId);
            } else {
                // SEQ_DS_DISPATCH_CONST_SET 시퀀스 미존재 → MAX+1 채번
                setId = tmsJdbc.queryForObject(
                    "SELECT NVL(MAX(SET_ID),0)+1 FROM KNRAWMS.TMS_DS_DISPATCH_CONST_SET", Integer.class);
                tmsJdbc.update("INSERT INTO KNRAWMS.TMS_DS_DISPATCH_CONST_SET (SET_ID,SET_NM,SET_DESC,ACTIVE_YN,SCOPE,CREDAT,LMODAT) VALUES (?,?,?,?,?,?,?)",
                    setId, nm, desc, act, scope, today(), today());
            }
            return Map.of("ok", true, "SET_ID", setId);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> setDelete(Map<String, Object> body) {
        Integer setId = toInteger(body.get("SET_ID"));
        if (setId == null) return Map.of("ok", false, "error", "SET_ID 필수");
        try {
            tmsJdbc.update("DELETE FROM KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM WHERE SET_ID=?", setId);
            tmsJdbc.update("DELETE FROM KNRAWMS.TMS_DS_DISPATCH_CONST_SET WHERE SET_ID=?", setId);
            tmsJdbc.update("UPDATE KNRAWMS.TMS_DS_DISPATCH_PROFILE SET SET_ID=NULL WHERE SET_ID=?", setId);
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
                    "FROM KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM i " +
                    "JOIN KNRAWMS.TMS_DS_DISPATCH_CONST c ON c.CONST_ID=i.CONST_ID " +
                    "WHERE i.SET_ID=? ORDER BY c.CONST_TYPE, c.SORT_SEQ, c.CONST_ID", setId
                );
            }
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) { return errMap(e); }
    }

    public Map<String, Object> setFull(Integer setId) {
        try {
            /* ── ① TMS_DS_DISPATCH_CONST 마스터 전체 조회 (tabMgr 전체 목록 표시용) ──
               PROFILE LEFT JOIN으로 orphan CONST도 포함. */
            List<Map<String, Object>> allConsts = tmsJdbc.queryForList(
                "SELECT c.CONST_ID, c.PROFILE_ID, c.CONST_TYPE, c.CONST_KEY, c.CONST_OP, " +
                "       c.CONST_VALUE, c.TARGET_ID, c.TARGET_NM, c.NOTE, c.ACTIVE_YN, c.SORT_SEQ, " +
                "       p.PROFILE_NM " +
                "FROM KNRAWMS.TMS_DS_DISPATCH_CONST c " +
                "LEFT JOIN KNRAWMS.TMS_DS_DISPATCH_PROFILE p ON p.PROFILE_ID=c.PROFILE_ID " +
                "ORDER BY c.CONST_TYPE, c.SORT_SEQ, c.CONST_ID"
            );

            /* ── ② TMS_DS_DISPATCH_CONST_SET_ITEM 조회 (세트에 저장된 항목 — IN_SET=1 기준) ──
               Oracle JDBC는 NUMBER를 BigDecimal로 반환하며 precision/scale 차이로
               BigDecimal.equals() 비교 시 miss 발생 가능 → String 키로 정규화. */
            Map<String, Map<String, Object>> includedMap = new HashMap<>();
            if (setId != null) {
                List<Map<String, Object>> items = tmsJdbc.queryForList(
                    "SELECT i.CONST_ID, i.ITEM_ID, i.ACTIVE_YN, i.PARAM_VALUE, " +
                    "       c.CONST_TYPE, c.CONST_KEY, c.CONST_OP, c.CONST_VALUE, " +
                    "       c.TARGET_ID, c.TARGET_NM, c.NOTE, c.ACTIVE_YN AS MASTER_YN, " +
                    "       c.SORT_SEQ, c.PROFILE_ID, p.PROFILE_NM " +
                    "FROM KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM i " +
                    "LEFT JOIN KNRAWMS.TMS_DS_DISPATCH_CONST c ON c.CONST_ID = i.CONST_ID " +
                    "LEFT JOIN KNRAWMS.TMS_DS_DISPATCH_PROFILE p ON p.PROFILE_ID = c.PROFILE_ID " +
                    "WHERE i.SET_ID = ? " +
                    "ORDER BY c.CONST_TYPE, c.SORT_SEQ, i.CONST_ID",
                    setId
                );
                for (Map<String, Object> it : items) {
                    Object cid = it.get("CONST_ID");
                    if (cid != null) includedMap.put(cid.toString(), it);
                }
            }

            /* ── ③ 마스터 전체 → IN_SET 플래그 부여 ── */
            Set<String> allConstIds = new java.util.HashSet<>();
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> c : allConsts) {
                Map<String, Object> d = new LinkedHashMap<>(c);
                Object constIdObj = d.get("CONST_ID");
                String constIdStr = constIdObj != null ? constIdObj.toString() : null;
                if (constIdStr != null) allConstIds.add(constIdStr);
                Map<String, Object> itemInfo = constIdStr != null ? includedMap.get(constIdStr) : null;
                d.put("IN_SET",      itemInfo != null ? 1 : 0);
                d.put("ITEM_ID",     itemInfo != null ? itemInfo.get("ITEM_ID") : null);
                /* IN_SET=0 항목의 ITEM_ACTIVE를 null로 → JS 'Y' 안전처리 */
                d.put("ITEM_ACTIVE", itemInfo != null ? itemInfo.get("ACTIVE_YN") : null);
                d.put("PARAM_VALUE", itemInfo != null ? itemInfo.get("PARAM_VALUE") : null);
                result.add(d);
            }

            /* ── ④ SET_ITEM에는 있지만 TMS_DS_DISPATCH_CONST 마스터에 없는 항목(orphan) 보완 ──
               세트에 저장된 CONST_ID가 마스터에 없더라도 IN_SET=1로 반드시 화면에 표시한다.
               SET_ITEM LEFT JOIN CONST 결과에서 CONST_KEY 등을 그대로 사용하므로
               저장 당시 마스터가 삭제된 경우에도 저장값은 유지·표시된다. */
            for (Map.Entry<String, Map<String, Object>> entry : includedMap.entrySet()) {
                if (!allConstIds.contains(entry.getKey())) {
                    Map<String, Object> item = entry.getValue();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("CONST_ID",   item.get("CONST_ID"));
                    row.put("PROFILE_ID", item.get("PROFILE_ID"));
                    /* SET_ITEM LEFT JOIN CONST 결과에서 CONST_TYPE 등 사용;
                       CONST가 없으면(null) GLOBAL로 fallback — 탭에 반드시 표시되도록. */
                    row.put("CONST_TYPE", item.get("CONST_TYPE") != null ? item.get("CONST_TYPE") : "GLOBAL");
                    row.put("CONST_KEY",  item.get("CONST_KEY")  != null ? item.get("CONST_KEY")  : "UNKNOWN_" + entry.getKey());
                    row.put("CONST_OP",   item.get("CONST_OP"));
                    row.put("CONST_VALUE",item.get("CONST_VALUE"));
                    row.put("TARGET_ID",  item.get("TARGET_ID"));
                    row.put("TARGET_NM",  item.get("TARGET_NM"));
                    row.put("NOTE",       item.get("NOTE"));
                    row.put("ACTIVE_YN",  item.get("MASTER_YN") != null ? item.get("MASTER_YN") : "Y");
                    row.put("SORT_SEQ",   item.get("SORT_SEQ")  != null ? item.get("SORT_SEQ")  : 9999);
                    row.put("PROFILE_NM", item.get("PROFILE_NM"));
                    row.put("IN_SET",      1);
                    row.put("ITEM_ID",     item.get("ITEM_ID"));
                    row.put("ITEM_ACTIVE", item.get("ACTIVE_YN"));
                    row.put("PARAM_VALUE", item.get("PARAM_VALUE"));
                    result.add(row);
                    log.warn("setFull orphan ITEM: CONST_ID={} SET_ITEM exists but not in TMS_DS_DISPATCH_CONST master", entry.getKey());
                }
            }
            return Map.of("ok", true, "rows", result);
        } catch (Exception e) { return errMap(e); }
    }

    public Map<String, Object> setVehicleTypes() {
        try {
            // TMS_DS_VEHICLE: tmsJdbc
            List<Map<String, Object>> vehicles = tmsJdbc.queryForList(
                "SELECT v.CARCLASS_CD, v.CARTYPE, v.LENGTH_M, v.WIDTH_M, v.HEIGHT_M, " +
                "       v.LOAD_TON, v.PALLET_HEIGHT_M, v.SORT_SEQ, " +
                "       v.PALLET_CNT, v.LONG_AXIS_YN, v.DEFAULT_VEH_CNT " +
                "FROM KNRAWMS.TMS_DS_VEHICLE v ORDER BY v.SORT_SEQ"
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
            tmsJdbc.update("DELETE FROM KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM WHERE SET_ID=? AND CONST_ID IN " +
                        "(SELECT CONST_ID FROM KNRAWMS.TMS_DS_DISPATCH_CONST WHERE CONST_TYPE='CARTYPE')", setId);
            // SEQ_DS_DISPATCH_CONST_SET_ITEM 시퀀스 미존재 → 루프 전 MAX+1 채번 시작값 확보
            Long nextItemId = tmsJdbc.queryForObject(
                "SELECT NVL(MAX(ITEM_ID),0)+1 FROM KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM", Long.class);
            int saved = 0;
            for (Map<String, Object> it : items) {
                if (!"Y".equals(it.get("active_yn"))) continue;
                String carclassCd = str(it.get("carclass_cd"));
                String cartype    = str(it.get("cartype"));
                String field      = str(it.get("field"));
                Object paramVal   = it.get("param_value");
                if (carclassCd.isBlank() || field.isBlank()) continue;

                // TMS_DS_DISPATCH_CONST 조회 또는 생성
                List<Map<String, Object>> existing = tmsJdbc.queryForList(
                    "SELECT CONST_ID FROM KNRAWMS.TMS_DS_DISPATCH_CONST WHERE CONST_TYPE='CARTYPE' AND CONST_KEY=? AND TARGET_ID=?",
                    field, carclassCd
                );
                Long constId;
                if (!existing.isEmpty()) {
                    constId = toLong(existing.get(0).get("CONST_ID"));
                } else {
                    // TMS_DS_VEHICLE에서 기본값
                    List<Map<String, Object>> vr = tmsJdbc.queryForList(
                        "SELECT * FROM KNRAWMS.TMS_DS_VEHICLE WHERE CARCLASS_CD=?", carclassCd
                    );
                    String defaultVal = vr.isEmpty() ? null : Objects.toString(vr.get(0).get(field), null);
                    // 첫 번째 프로파일 ID
                    List<Map<String, Object>> pr = tmsJdbc.queryForList(
                        "SELECT PROFILE_ID FROM KNRAWMS.TMS_DS_DISPATCH_PROFILE ORDER BY PROFILE_ID FETCH FIRST 1 ROWS ONLY"
                    );
                    Long profileId = pr.isEmpty() ? 1L : toLong(pr.get(0).get("PROFILE_ID"));
                    String constOp = List.of("ALLOW_CARTYPE","LONG_AXIS_YN").contains(field) ? "=" : "<=";
                    constId = nextConstId();
                    tmsJdbc.update("INSERT INTO KNRAWMS.TMS_DS_DISPATCH_CONST (CONST_ID,PROFILE_ID,CONST_TYPE,CONST_KEY,CONST_VALUE,CONST_OP,TARGET_ID,TARGET_NM,ACTIVE_YN,NOTE,SORT_SEQ,CREDAT,LMODAT) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        constId, profileId, "CARTYPE", field, vc(defaultVal), constOp, carclassCd, cartype, "Y", "차량유형관리 연동", 0, today(), today());
                }
                tmsJdbc.update("INSERT INTO KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM (ITEM_ID,SET_ID,CONST_ID,ACTIVE_YN,PARAM_VALUE) VALUES (?,?,?,?,?)",
                    nextItemId++, setId, constId, "Y", vc(paramVal));
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
            // TMS_SHPDH JOIN BZPTN JOIN BZPTN_DETAIL — tmsJdbc 단독 (동일 DB이므로 JOIN 가능)
            // REGION_YN은 BZPTN_DETAIL에 없음 → '' 리터럴로 대체 (TMS 측 관리 예정)
            List<Map<String, Object>> partners = tmsJdbc.queryForList(
                "SELECT DISTINCT h.DPTNKY AS PTNRKY, COALESCE(b.NAME01,h.DPTNKY) AS NAME01, " +
                "       COALESCE(b.POSTCD,'') AS POSTCD, COALESCE(d.AREA_CD,'') AS AREA_CD, " +
                "       '' AS REGION_YN " +
                "FROM KNRAWMS.TMS_SHPDH h LEFT JOIN KNRAWMS.BZPTN b ON b.PTNRKY=h.DPTNKY AND b.PTNRTY='CT' " +
                "LEFT JOIN KNRAWMS.BZPTN_DETAIL d ON d.PTNRKY=h.DPTNKY AND d.PTNRTY='CT' " +
                "WHERE h.DPTNKY IS NOT NULL AND h.DPTNKY <> ' ' ORDER BY h.DPTNKY"
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
        // REGION_YN 컬럼이 Oracle KNRAWMS.BZPTN_DETAIL에 미존재 — DB DDL 미완료로 저장 비활성
        // 저장 흐름을 차단하지 않도록 no-op(saved:0)으로 성공 반환, DDL 완료 후 실제 구현 예정
        return Map.of("ok", true, "saved", 0);
    }

    public Map<String, Object> setEntryTonList() { return setEntryTonList("PS"); }
    public Map<String, Object> setEntryTonList(String scopeIn) {
        try {
            String scope   = normScope(scopeIn);
            String ptnl01  = ptnl01Of(scope);            // PS='10' / HL='20'
            // tmsJdbc 단독 — BZPTN JOIN BZPTN_DETAIL (동일 DB/계정이므로 JOIN 가능)
            // b.PTNL01=? : 스코프별 납품처 대상만 조회 (PS=판지 '10' / HL=생활 '20')
            List<Map<String, Object>> partners = tmsJdbc.queryForList(
                "SELECT b.PTNRKY, 'CT' AS PTNRTY, " +
                "       COALESCE(d.OWNRKY,'KN') AS OWNRKY, " +
                "       COALESCE(d.WAREKY,'W001') AS WAREKY, " +
                "       COALESCE(b.NAME01,b.PTNRKY) AS NAME01, " +
                "       d.AREA_CD, d.MAX_TON, d.AUTO_ALLOC_YN " +
                "FROM KNRAWMS.BZPTN b " +
                "LEFT JOIN KNRAWMS.BZPTN_DETAIL d ON d.PTNRKY=b.PTNRKY AND d.PTNRTY=b.PTNRTY AND d.OWNRKY=b.OWNRKY " +
                "WHERE b.PTNRTY='CT' AND b.PTNL01=? ORDER BY d.AREA_CD, b.PTNRKY",
                ptnl01
            );
            List<Map<String, Object>> carclasses = wmsJdbc.queryForList(
                "SELECT CMCDVL AS value, CDESC1 AS label FROM KNRAWMS.CMCDV WHERE CMCDKY=? ORDER BY CMCDVL",
                carclassKey(scope)
            );
            return Map.of("ok", true, "partners", partners, "carclasses", carclasses);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> setEntryTonSave(Map<String, Object> body) {
        return bzptnDetailBatchSave(body, "MAX_TON");
    }

    public Map<String, Object> setForkliftList() { return setForkliftList("PS"); }
    public Map<String, Object> setForkliftList(String scopeIn) {
        try {
            String ptnl01 = ptnl01Of(normScope(scopeIn));   // PS='10' / HL='20'
            // tmsJdbc 단독 — BZPTN JOIN BZPTN_DETAIL (동일 DB/계정이므로 JOIN 가능)
            // b.PTNL01=? : 스코프별 납품처 대상만 조회 (PS=판지 '10' / HL=생활 '20')
            List<Map<String, Object>> rows = tmsJdbc.queryForList(
                "SELECT b.PTNRKY, 'CT' AS PTNRTY, " +
                "       COALESCE(d.OWNRKY,'KN') AS OWNRKY, " +
                "       COALESCE(d.WAREKY,'W001') AS WAREKY, " +
                "       COALESCE(b.NAME01,b.PTNRKY) AS NAME01, " +
                "       d.AREA_CD, d.FORKLIFT_YN, d.AUTO_ALLOC_YN " +
                "FROM KNRAWMS.BZPTN b " +
                "LEFT JOIN KNRAWMS.BZPTN_DETAIL d ON d.PTNRKY=b.PTNRKY AND d.PTNRTY=b.PTNRTY AND d.OWNRKY=b.OWNRKY " +
                "WHERE b.PTNRTY='CT' AND b.PTNL01=? ORDER BY d.AREA_CD, b.PTNRKY",
                ptnl01
            );
            return Map.of("ok", true, "partners", rows);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> setForkliftSave(Map<String, Object> body) {
        return bzptnDetailBatchSave(body, "FORKLIFT_YN");
    }

    public Map<String, Object> setDynamicList() { return setDynamicList("PS"); }
    public Map<String, Object> setDynamicList(String scopeIn) {
        try {
            String ptnl01 = ptnl01Of(normScope(scopeIn));   // PS='10' / HL='20'
            // tmsJdbc 단독 — BZPTN JOIN BZPTN_DETAIL (동일 DB/계정이므로 JOIN 가능)
            // b.PTNL01=? : 스코프별 납품처 대상만 조회 (PS=판지 '10' / HL=생활 '20')
            List<Map<String, Object>> rows = tmsJdbc.queryForList(
                "SELECT b.PTNRKY, 'CT' AS PTNRTY, " +
                "       COALESCE(d.OWNRKY,'KN') AS OWNRKY, " +
                "       COALESCE(d.WAREKY,'W001') AS WAREKY, " +
                "       COALESCE(b.NAME01,b.PTNRKY) AS NAME01, " +
                "       d.AREA_CD, d.DYNAMIC_YN, d.AUTO_ALLOC_YN " +
                "FROM KNRAWMS.BZPTN b " +
                "LEFT JOIN KNRAWMS.BZPTN_DETAIL d ON d.PTNRKY=b.PTNRKY AND d.PTNRTY=b.PTNRTY AND d.OWNRKY=b.OWNRKY " +
                "WHERE b.PTNRTY='CT' AND b.PTNL01=? ORDER BY d.AREA_CD, b.PTNRKY",
                ptnl01
            );
            return Map.of("ok", true, "partners", rows);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> setDynamicSave(Map<String, Object> body) {
        return bzptnDetailBatchSave(body, "DYNAMIC_YN");
    }

    // ══════════════════════════════════════════════════════════════
    //  납품처 통합 제약 (PTNR_MULTI) — 1개 탭으로 4개 제약 동시 관리
    //   1) DYNAMIC_DIST_M  동적거리(동적 허용 거리, 숫자)
    //   2) HANDWORK_YN     수작업 (Y/N)
    //   3) AUTO_ALLOC_YN   자동배차유무 (Y/N)
    //   4) DYNAMIC_YN      동적대상 사용유무 (Y/N)
    //  대상 테이블: KNRAWMS.BZPTN_DETAIL (납품처 관리와 동일)
    // ══════════════════════════════════════════════════════════════
    public Map<String, Object> setPtnrMultiList() { return setPtnrMultiList("PS"); }
    public Map<String, Object> setPtnrMultiList(String scopeIn) {
        try {
            String ptnl01 = ptnl01Of(normScope(scopeIn));   // PS='10' / HL='20'
            // tmsJdbc 단독 — BZPTN JOIN BZPTN_DETAIL (동일 DB/계정이므로 JOIN 가능)
            // b.PTNL01=? : 스코프별 납품처 대상만 조회 (PS=판지 '10' / HL=생활 '20')
            List<Map<String, Object>> rows = tmsJdbc.queryForList(
                "SELECT b.PTNRKY, 'CT' AS PTNRTY, " +
                "       COALESCE(d.OWNRKY,'KN') AS OWNRKY, " +
                "       COALESCE(d.WAREKY,'W001') AS WAREKY, " +
                "       COALESCE(b.NAME01,b.PTNRKY) AS NAME01, " +
                "       d.AREA_CD, d.DYNAMIC_DIST_M, d.HANDWORK_YN, d.AUTO_ALLOC_YN, d.DYNAMIC_YN " +
                "FROM KNRAWMS.BZPTN b " +
                "LEFT JOIN KNRAWMS.BZPTN_DETAIL d ON d.PTNRKY=b.PTNRKY AND d.PTNRTY=b.PTNRTY AND d.OWNRKY=b.OWNRKY " +
                "WHERE b.PTNRTY='CT' AND b.PTNL01=? ORDER BY d.AREA_CD, b.PTNRKY",
                ptnl01
            );
            return Map.of("ok", true, "partners", rows);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> setPtnrMultiSave(Map<String, Object> body) {
        return bzptnDetailMultiColSave(body,
            new String[]{ "DYNAMIC_DIST_M", "HANDWORK_YN", "AUTO_ALLOC_YN", "DYNAMIC_YN" });
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> setItemsSave(Map<String, Object> body) {
        Integer setId = toInteger(body.get("set_id"));
        if (setId == null) return Map.of("ok", false, "error", "set_id 필수");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null) items = Collections.emptyList();
        try {
            /* CARTYPE 타입 항목은 cartypeSave()에서 별도 관리하므로 여기서는 삭제 제외.
               전체 DELETE를 하면 cartypeItems=0일 때 CARTYPE 데이터가 영구 소실된다. */
            tmsJdbc.update(
                "DELETE FROM KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM " +
                "WHERE SET_ID=? AND CONST_ID NOT IN " +
                "(SELECT CONST_ID FROM KNRAWMS.TMS_DS_DISPATCH_CONST WHERE CONST_TYPE='CARTYPE')",
                setId);
            // SEQ_DS_DISPATCH_CONST_SET_ITEM 시퀀스 미존재 → 루프 전 MAX+1 로 채번 시작값 확보
            Long nextItemId = tmsJdbc.queryForObject(
                "SELECT NVL(MAX(ITEM_ID),0)+1 FROM KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM", Long.class);
            /* ── [ORA-00001 방지] 동일 (SET_ID, CONST_ID) 중복 INSERT 방지 ──────────
               UK_DS_CONST_SET_ITEM = UNIQUE(SET_ID, CONST_ID).
               (1) items 안에 같은 CONST_ID 가 2번 들어오거나(기존행+자동보충 카드가
                   동일 마스터로 귀결), (2) findOrCreateConstMaster 가 재사용한 CONST_ID 가
                   DELETE 제외 대상(CARTYPE)과 겹치면 동일 (SET_ID,CONST_ID) 가 중복 INSERT
                   되어 ORA-00001 발생. → 이미 처리한 CONST_ID 는 건너뛰고(seen), INSERT 전
                   존재 여부를 확인해 있으면 UPDATE(upsert)로 처리한다. */
            Set<Long> seen = new HashSet<>();
            int saved = 0;
            for (Map<String, Object> it : items) {
                Long constId = toLong(it.get("const_id"));
                /* ── const_id 미존재(신규 파라미터 키) → 마스터 자동 find-or-create ──
                   ALLOW_MATERIAL_MIX / MIX_3D_CHECK_YN 등 신규 파라미터 키는 TMS_DS_DISPATCH_CONST
                   마스터 행이 없어 프론트 3-tier 매칭이 실패한다. 이때 프론트는 const_id=null +
                   const_key/const_type 를 전송하므로, 여기서 마스터를 find-or-create 한 뒤 저장한다. */
                if (constId == null) {
                    String ckey = str(it.get("const_key"));
                    if (ckey.isBlank()) continue;   // 키도 없으면 스킵
                    String ctype = str(it.getOrDefault("const_type", "GLOBAL"));
                    if (ctype.isBlank()) ctype = "GLOBAL";
                    constId = findOrCreateConstMaster(
                        ckey, ctype,
                        str(it.getOrDefault("const_op", "=")),
                        str(it.get("target_id")),
                        str(it.get("target_nm")),
                        str(it.get("const_value")),
                        setId                       // 이 세트를 사용하는 프로파일에 마스터 생성
                    );
                }
                if (constId == null) continue;
                // 동일 요청 내 같은 CONST_ID 중복 처리 방지(뒤 항목이 앞 항목을 덮어쓰지 않도록 1회만)
                if (!seen.add(constId)) continue;
                String yn   = Objects.toString(it.get("active_yn"), "Y").trim();
                Object pval = it.get("param_value");
                /* 존재 시 UPDATE, 없으면 INSERT (upsert) — (SET_ID,CONST_ID) 유니크 보장 */
                int upd = tmsJdbc.update(
                    "UPDATE KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM SET ACTIVE_YN=?, PARAM_VALUE=? WHERE SET_ID=? AND CONST_ID=?",
                    yn, vc(pval), setId, constId);
                if (upd == 0) {
                    tmsJdbc.update("INSERT INTO KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM (ITEM_ID,SET_ID,CONST_ID,ACTIVE_YN,PARAM_VALUE) VALUES (?,?,?,?,?)",
                        nextItemId++, setId, constId, yn, vc(pval));
                }
                saved++;
            }
            return Map.of("ok", true, "saved", saved);
        } catch (Exception e) { return errMap(e); }
    }

    /* ── 신규 파라미터 키(예: ALLOW_MATERIAL_MIX)의 TMS_DS_DISPATCH_CONST 마스터 find-or-create ──
       세트 항목 저장 시 const_id 가 없는 신규 키를 처리한다. 동일 CONST_KEY(+TARGET_ID) 가
       이미 있으면 그 CONST_ID 재사용, 없으면 첫 번째 프로파일에 마스터를 생성한다. */
    private Long findOrCreateConstMaster(String key, String type, String op,
                                         String targetId, String targetNm, String constValue,
                                         Integer setId) {
        if (key == null || key.isBlank()) return null;
        String tid = (targetId == null) ? "" : targetId.trim();
        // ── 대상 프로파일 결정 (중요) ──────────────────────────────────
        //  신규 제약 마스터는 '자동배차가 실제로 읽는 프로파일'에 생성해야 한다.
        //  자동배차는 PROFILE.SET_ID 로 세트를 참조하므로, 이 세트(setId)를 연결한
        //  프로파일을 최우선 대상으로 삼는다. (없으면 세트 scope의 활성 프로파일,
        //  그래도 없으면 전체 활성 프로파일 → 최후에 PROFILE_ID 최솟값)
        Long targetProfileId = resolveSetProfileId(setId);
        // ① 기존 마스터 재사용 — 단, '대상 프로파일'에 이미 같은 CONST_KEY(+TARGET_ID)가
        //    있으면 그것을 재사용. (다른 프로파일의 동일 키를 잘못 집어 자동배차가 못 읽던 버그 방지)
        List<Map<String, Object>> existing;
        if (tid.isBlank()) {
            existing = (targetProfileId != null)
                ? tmsJdbc.queryForList(
                    "SELECT CONST_ID FROM KNRAWMS.TMS_DS_DISPATCH_CONST " +
                    "WHERE CONST_KEY=? AND PROFILE_ID=? AND (TARGET_ID IS NULL OR TARGET_ID='') " +
                    "ORDER BY CONST_ID FETCH FIRST 1 ROWS ONLY", key, targetProfileId)
                : tmsJdbc.queryForList(
                    "SELECT CONST_ID FROM KNRAWMS.TMS_DS_DISPATCH_CONST " +
                    "WHERE CONST_KEY=? AND (TARGET_ID IS NULL OR TARGET_ID='') ORDER BY CONST_ID FETCH FIRST 1 ROWS ONLY",
                    key);
        } else {
            existing = (targetProfileId != null)
                ? tmsJdbc.queryForList(
                    "SELECT CONST_ID FROM KNRAWMS.TMS_DS_DISPATCH_CONST " +
                    "WHERE CONST_KEY=? AND TARGET_ID=? AND PROFILE_ID=? ORDER BY CONST_ID FETCH FIRST 1 ROWS ONLY",
                    key, tid, targetProfileId)
                : tmsJdbc.queryForList(
                    "SELECT CONST_ID FROM KNRAWMS.TMS_DS_DISPATCH_CONST " +
                    "WHERE CONST_KEY=? AND TARGET_ID=? ORDER BY CONST_ID FETCH FIRST 1 ROWS ONLY",
                    key, tid);
        }
        if (!existing.isEmpty()) {
            Long cid = toLong(existing.get(0).get("CONST_ID"));
            /* ── [핵심 버그 수정] 기존 마스터 재사용 시 CONST_VALUE 갱신(upsert) ──
               기존에는 마스터를 '재사용'만 하고 CONST_VALUE 를 갱신하지 않아,
               한 번 N 으로 생성된 Y/N 토글 제약(MIX_UNIFIED_VEHICLE_YN 등)이
               세트 편집에서 Y 로 바꿔 저장해도 마스터 값은 N 그대로 남았다.
               (자동배차는 마스터 CONST_VALUE 를 읽으므로 계속 N → 통합배차 미동작)
               → 사용자가 명시적으로 값을 전달(constValue != null/blank)한 경우,
                 재사용 마스터의 CONST_VALUE 와 ACTIVE_YN 을 입력값으로 upsert 한다. */
            if (constValue != null && !constValue.isBlank()) {
                try {
                    tmsJdbc.update(
                        "UPDATE KNRAWMS.TMS_DS_DISPATCH_CONST SET CONST_VALUE=?, ACTIVE_YN='Y', LMODAT=? WHERE CONST_ID=?",
                        constValue.trim(), today(), cid);
                    log.info("[dcon] findOrCreateConstMaster upsert: CONST_ID={}, KEY={}, VALUE={}", cid, key, constValue.trim());
                } catch (Exception e) {
                    log.warn("[dcon] findOrCreateConstMaster upsert 실패 (CONST_ID={}): {}", cid, e.getMessage());
                }
            }
            return cid;
        }
        // ② 없으면 '대상 프로파일'에 마스터 생성 (최후 폴백: PROFILE_ID 최솟값)
        Long profileId = targetProfileId;
        if (profileId == null) {
            List<Map<String, Object>> pr = tmsJdbc.queryForList(
                "SELECT PROFILE_ID FROM KNRAWMS.TMS_DS_DISPATCH_PROFILE ORDER BY PROFILE_ID FETCH FIRST 1 ROWS ONLY");
            profileId = pr.isEmpty() ? 1L : toLong(pr.get(0).get("PROFILE_ID"));
        }
        String ctype = (type == null || type.isBlank()) ? "GLOBAL" : type;
        String cop   = (op == null || op.isBlank())     ? "="      : op;
        Long newCid = nextConstId();
        // ⚠️ TARGET_ID/TARGET_NM 은 null 가능 → raw null 바인딩 시 ORA-17004(열 유형 부적합)
        //   발생. VARCHAR 타입을 명시하는 vc() 로 감싸 null-safe 바인딩한다.
        tmsJdbc.update(
            "INSERT INTO KNRAWMS.TMS_DS_DISPATCH_CONST (CONST_ID,PROFILE_ID,CONST_TYPE,CONST_KEY,CONST_VALUE,CONST_OP,TARGET_ID,TARGET_NM,ACTIVE_YN,NOTE,SORT_SEQ,CREDAT,LMODAT) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            newCid, profileId, ctype, key, (constValue == null ? "" : constValue), cop,
            vc(tid.isBlank() ? null : tid), vc(targetNm == null ? null : targetNm),
            "Y", "제약조건관리 신규 항목 자동생성", 999, today(), today());
        return newCid;
    }

    /**
     * 세트(setId)가 연결된 '자동배차가 읽는 프로파일'의 PROFILE_ID 를 해석.
     *  ① PROFILE.SET_ID = setId 인 프로파일(자동배차가 이 세트를 로드하는 프로파일) 우선.
     *     - 활성(ACTIVE_YN='Y') 우선, PROFILE_ID 오름차순.
     *  ② 없으면 세트 scope 의 활성 프로파일.
     *  ③ 그래도 없으면 null (호출부가 PROFILE_ID 최솟값으로 폴백).
     */
    private Long resolveSetProfileId(Integer setId) {
        if (setId == null) return null;
        try {
            // ① 이 세트를 연결한 프로파일 (활성 우선)
            List<Map<String, Object>> bySet = tmsJdbc.queryForList(
                "SELECT PROFILE_ID FROM KNRAWMS.TMS_DS_DISPATCH_PROFILE WHERE SET_ID=? " +
                "ORDER BY CASE WHEN ACTIVE_YN='Y' THEN 0 ELSE 1 END, PROFILE_ID FETCH FIRST 1 ROWS ONLY",
                setId);
            if (!bySet.isEmpty()) return toLong(bySet.get(0).get("PROFILE_ID"));
            // ② 세트 scope 의 활성 프로파일
            List<Map<String, Object>> scopeRow = tmsJdbc.queryForList(
                "SELECT COALESCE(SCOPE,'PS') AS SCOPE FROM KNRAWMS.TMS_DS_DISPATCH_CONST_SET WHERE SET_ID=?",
                setId);
            String scope = scopeRow.isEmpty() ? "PS" : str(scopeRow.get(0).get("SCOPE"));
            List<Map<String, Object>> act = tmsJdbc.queryForList(
                "SELECT PROFILE_ID FROM KNRAWMS.TMS_DS_DISPATCH_PROFILE " +
                "WHERE ACTIVE_YN='Y' AND COALESCE(SCOPE,'PS')=? ORDER BY PROFILE_ID FETCH FIRST 1 ROWS ONLY",
                scope);
            if (!act.isEmpty()) return toLong(act.get(0).get("PROFILE_ID"));
        } catch (Exception e) {
            log.warn("[dcon] resolveSetProfileId 실패 (setId={}): {}", setId, e.getMessage());
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════
    //  제약조건 프로파일 (TMS_DS_DISPATCH_PROFILE + TMS_DS_DISPATCH_CONST) — MariaDB
    // ══════════════════════════════════════════════════════════════

    public Map<String, Object> profiles() { return profiles("PS"); }

    public Map<String, Object> profiles(String scopeIn) {
        try {
            String scope = normScope(scopeIn);
            List<Map<String, Object>> rows = tmsJdbc.queryForList(
                "SELECT * FROM KNRAWMS.TMS_DS_DISPATCH_PROFILE WHERE COALESCE(SCOPE,'PS')=? ORDER BY PROFILE_ID", scope
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
            String scope = normScope(body.get("SCOPE"));
            if (nm.isBlank()) return Map.of("ok", false, "error", "PROFILE_NM 필수");

            if (pid != null) {
                // 수정 시 SCOPE 유지
                tmsJdbc.update("UPDATE KNRAWMS.TMS_DS_DISPATCH_PROFILE SET PROFILE_NM=?,OBJECTIVE=?,ACTIVE_YN=?,NOTE=?,LMODAT=? WHERE PROFILE_ID=?",
                    nm, obj, act, note, today(), pid);
            } else {
                // PROFILE_ID 채번: 시퀀스 존재 시 NEXTVAL, 없으면 MAX+1 폴백(ORA-02289 방지).
                pid = nextProfileId();
                tmsJdbc.update("INSERT INTO KNRAWMS.TMS_DS_DISPATCH_PROFILE (PROFILE_ID,PROFILE_NM,OBJECTIVE,ACTIVE_YN,NOTE,SCOPE,CREDAT,LMODAT) VALUES (?,?,?,?,?,?,?,?)",
                    pid, nm, obj, act, note, scope, today(), today());
            }
            return Map.of("ok", true, "PROFILE_ID", pid);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> profileDelete(Map<String, Object> body) {
        Long pid = toLong(body.get("PROFILE_ID"));
        if (pid == null) return Map.of("ok", false, "error", "PROFILE_ID 필수");
        try {
            tmsJdbc.update("DELETE FROM KNRAWMS.TMS_DS_DISPATCH_CONST WHERE PROFILE_ID=?", pid);
            tmsJdbc.update("DELETE FROM KNRAWMS.TMS_DS_DISPATCH_PROFILE WHERE PROFILE_ID=?", pid);
            return Map.of("ok", true);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> profileLinkSet(Map<String, Object> body) {
        Long profId = toLong(body.get("profile_id"));
        Integer setId = toInteger(body.get("set_id"));
        if (profId == null) return Map.of("ok", false, "error", "profile_id 필수");
        try {
            // SET_ID(NUMBER)는 연결 해제 시 null 가능 → 타입 명시 바인딩(ORA-17004 방지)
            Object setIdBind = new SqlParameterValue(Types.NUMERIC, setId);
            tmsJdbc.update("UPDATE KNRAWMS.TMS_DS_DISPATCH_PROFILE SET SET_ID=?, LMODAT=? WHERE PROFILE_ID=?", setIdBind, today(), profId);
            return Map.of("ok", true);
        } catch (Exception e) { return errMap(e); }
    }

    public Map<String, Object> constraintAll() {
        try {
            List<Map<String, Object>> rows = tmsJdbc.queryForList(
                "SELECT c.*, p.PROFILE_NM FROM KNRAWMS.TMS_DS_DISPATCH_CONST c " +
                "JOIN KNRAWMS.TMS_DS_DISPATCH_PROFILE p ON p.PROFILE_ID=c.PROFILE_ID " +
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
                    "SELECT * FROM KNRAWMS.TMS_DS_DISPATCH_CONST WHERE PROFILE_ID=? ORDER BY SORT_SEQ,CONST_ID", profileId
                );
            } else {
                rows = tmsJdbc.queryForList("SELECT * FROM KNRAWMS.TMS_DS_DISPATCH_CONST ORDER BY PROFILE_ID,SORT_SEQ,CONST_ID");
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
            tmsJdbc.update("UPDATE KNRAWMS.TMS_DS_DISPATCH_CONST SET PROFILE_ID=?,CONST_TYPE=?,CONST_KEY=?,CONST_VALUE=?,CONST_OP=?,TARGET_ID=?,TARGET_NM=?,ACTIVE_YN=?,NOTE=?,SORT_SEQ=?,LMODAT=? WHERE CONST_ID=?",
                pid, type, key, val, op, tid, tnm, act, note, sort, today(), cid);
            return cid;
        } else {
            // SCOPE: body 값 우선, 없으면 소속 프로파일의 SCOPE 를 상속(둘 다 없으면 'PS').
            String scope = row.get("SCOPE") != null ? normScope(row.get("SCOPE")) : profileScope(pid);
            Long newCid = nextConstId();
            tmsJdbc.update("INSERT INTO KNRAWMS.TMS_DS_DISPATCH_CONST (CONST_ID,PROFILE_ID,CONST_TYPE,CONST_KEY,CONST_VALUE,CONST_OP,TARGET_ID,TARGET_NM,ACTIVE_YN,NOTE,SORT_SEQ,SCOPE,CREDAT,LMODAT) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                newCid, pid, type, key, val, op, tid, tnm, act, note, sort, scope, today(), today());
            return newCid;
        }
    }

    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> constraintDelete(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> ids = (List<Object>) body.get("ids");
        if (ids == null || ids.isEmpty()) return Map.of("ok", false, "error", "ids 필수");
        try {
            String ph = String.join(",", Collections.nCopies(ids.size(), "?"));
            tmsJdbc.update("DELETE FROM KNRAWMS.TMS_DS_DISPATCH_CONST WHERE CONST_ID IN (" + ph + ")", ids.toArray());
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
                "SELECT * FROM KNRAWMS.TMS_DS_DISPATCH_PROFILE WHERE PROFILE_ID=?", srcPid
            );
            if (src.isEmpty()) return Map.of("ok", false, "error", "원본 프로파일 없음");
            Map<String, Object> s = src.get(0);
            // 복사본은 원본 프로파일과 동일 SCOPE 유지(PS→PS / HL→HL).
            String scope = normScope(s.get("SCOPE"));
            // PROFILE_ID 채번: 시퀀스 존재 시 NEXTVAL, 없으면 MAX+1 폴백(ORA-02289 방지).
            Long newPid = nextProfileId();
            tmsJdbc.update("INSERT INTO KNRAWMS.TMS_DS_DISPATCH_PROFILE (PROFILE_ID,PROFILE_NM,OBJECTIVE,ACTIVE_YN,NOTE,SCOPE,CREDAT,LMODAT) VALUES (?,?,?,?,?,?,?,?)",
                newPid, newNm, s.get("OBJECTIVE"), "N", "복사본: " + s.get("PROFILE_NM"), scope, today(), today());
            List<Map<String, Object>> srcRows = tmsJdbc.queryForList(
                "SELECT * FROM KNRAWMS.TMS_DS_DISPATCH_CONST WHERE PROFILE_ID=?", srcPid
            );
            for (Map<String, Object> r : srcRows) {
                tmsJdbc.update("INSERT INTO KNRAWMS.TMS_DS_DISPATCH_CONST (CONST_ID,PROFILE_ID,CONST_TYPE,CONST_KEY,CONST_VALUE,CONST_OP,TARGET_ID,TARGET_NM,ACTIVE_YN,NOTE,SORT_SEQ,SCOPE,CREDAT,LMODAT) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    nextConstId(), newPid, r.get("CONST_TYPE"), r.get("CONST_KEY"), r.get("CONST_VALUE"), r.get("CONST_OP"),
                    r.get("TARGET_ID"), r.get("TARGET_NM"), r.get("ACTIVE_YN"), r.get("NOTE"), r.get("SORT_SEQ"), scope, today(), today());
            }
            return Map.of("ok", true, "new_profile_id", newPid);
        } catch (Exception e) { return errMap(e); }
    }

    public Map<String, Object> constraintMeta() { return constraintMeta("PS"); }

    public Map<String, Object> constraintMeta(String scopeIn) {
        try {
            String scope = normScope(scopeIn);
            // TMS_DS_VEHICLE: MariaDB
            List<Map<String, Object>> vehicles = tmsJdbc.queryForList(
                "SELECT CARCLASS_CD, CARTYPE, LOAD_TON, LENGTH_M, WIDTH_M, HEIGHT_M, PALLET_HEIGHT_M, SORT_SEQ " +
                "FROM KNRAWMS.TMS_DS_VEHICLE ORDER BY SORT_SEQ"
            );
            // CMCDV: Oracle KNRAWMS — 스코프별 제품군(PS=TMS_CARCLASS10 / HL=TMS_CARCLASS20)
            List<Map<String, Object>> carclasses = wmsJdbc.queryForList(
                "SELECT CMCDVL, CDESC1 FROM KNRAWMS.CMCDV WHERE CMCDKY=? ORDER BY CMCDVL", carclassKey(scope)
            );
            // TMS_ROUTE_COST JOIN BZPTN — tmsJdbc 단독 (동일 DB/계정이므로 JOIN 가능)
            List<Map<String, Object>> partners = tmsJdbc.queryForList(
                "SELECT DISTINCT r.PTNRKY, COALESCE(b.NAME01,r.PTNRKY) AS PTNRNM " +
                "FROM KNRAWMS.TMS_ROUTE_COST r " +
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
        // 스코프별 납품처 제품군 검증 기준 (PS='10' / HL='20')
        String ptnl01 = ptnl01Of(normScope(body.get("scope")));
        String lmodat = today();
        String lmotim = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        int saved = 0;
        try {
            for (Map<String, Object> it : items) {
                String ptnrky = str(it.get("ptnrky"));
                String ptnrty = Objects.toString(it.get("ptnrty"), "CT").trim();
                String ownrky = Objects.toString(it.get("ownrky"), "KN").trim();
                String wareky = Objects.toString(it.getOrDefault("wareky", "W001"), "W001").trim();
                Object colValRaw = it.get(columnName.toLowerCase());
                if (colValRaw == null) colValRaw = it.get(columnName);
                if (colValRaw instanceof String && ((String) colValRaw).isBlank()) colValRaw = null;
                // null-safe 바인딩(ORA-17004 방지): VARCHAR NULL → 대상 컬럼 타입으로 안전 변환
                Object colVal = vc(colValRaw);
                if (ptnrky.isBlank()) continue;
                // 스코프 납품처(PTNL01=?) 대상인지 검증 — 조회 대상과 저장 대상 일치 보장
                int psCount = tmsJdbc.queryForObject(
                    "SELECT COUNT(*) FROM KNRAWMS.BZPTN WHERE PTNRKY=? AND PTNRTY='CT' AND PTNL01=?",
                    Integer.class, ptnrky, ptnl01
                );
                if (psCount == 0) { saved++; continue; } // 스코프 대상 아님 → 건너뜀

                // Oracle MERGE INTO — UK_BZPTN_DETAIL 는 (PTNRKY, PTNRTY, OWNRKY) 3컬럼.
                // 제약관리 화면에는 WAREKY(거점) 선택 기능이 없으므로 ON 절 식별키에서 WAREKY 제외.
                // WAREKY는 INSERT 시 기본값 기록용으로만 사용하며, UPDATE 시에는 기존 값 유지.
                tmsJdbc.update(
                    "MERGE INTO KNRAWMS.BZPTN_DETAIL t " +
                    "USING (SELECT ? AS PTNRKY, ? AS PTNRTY, ? AS OWNRKY FROM DUAL) s " +
                    "ON (t.PTNRKY=s.PTNRKY AND t.PTNRTY=s.PTNRTY AND t.OWNRKY=s.OWNRKY) " +
                    "WHEN MATCHED THEN UPDATE SET t." + columnName + "=?, t.LMODAT=?, t.LMOTIM=?, t.LMOUSR='DCON_SET' " +
                    "WHEN NOT MATCHED THEN INSERT (PTNRKY,PTNRTY,OWNRKY,WAREKY," + columnName + ",LMODAT,LMOTIM,LMOUSR) " +
                    "VALUES (?,?,?,?,?,?,?,'DCON_SET')",
                    ptnrky, ptnrty, ownrky,             // USING 3개 (WAREKY 제거)
                    colVal, lmodat, lmotim,              // WHEN MATCHED UPDATE
                    ptnrky, ptnrty, ownrky, wareky, colVal, lmodat, lmotim  // WHEN NOT MATCHED INSERT
                );
                saved++;
            }
            return Map.of("ok", true, "saved", saved);
        } catch (Exception e) { return errMap(e); }
    }

    /**
     * BZPTN_DETAIL 여러 컬럼을 납품처별로 한 번에 MERGE 저장.
     * items[i] 는 { ptnrky, ptnrty, ownrky, wareky, <컬럼소문자>:값 ... } 형태.
     * columnNames 에 지정된 컬럼만 UPDATE/INSERT 대상으로 반영한다.
     * (PTNR_MULTI 통합 탭 — 동적거리/수작업/자동배차/동적대상 4컬럼 동시 저장)
     */
    private Map<String, Object> bzptnDetailMultiColSave(Map<String, Object> body, String[] columnNames) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) return Map.of("ok", true, "saved", 0);
        if (columnNames == null || columnNames.length == 0) return Map.of("ok", true, "saved", 0);
        // 스코프별 납품처 제품군 검증 기준 (PS='10' / HL='20')
        String ptnl01 = ptnl01Of(normScope(body.get("scope")));
        String lmodat = today();
        String lmotim = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        int saved = 0;
        try {
            // 동적 SET / INSERT 컬럼 절 생성
            StringBuilder setClause = new StringBuilder();   // UPDATE SET
            StringBuilder insCols   = new StringBuilder();   // INSERT 컬럼
            StringBuilder insVals   = new StringBuilder();   // INSERT VALUES placeholders
            for (String col : columnNames) {
                setClause.append("t.").append(col).append("=?, ");
                insCols.append(col).append(",");
                insVals.append("?,");
            }
            String sql =
                "MERGE INTO KNRAWMS.BZPTN_DETAIL t " +
                "USING (SELECT ? AS PTNRKY, ? AS PTNRTY, ? AS OWNRKY FROM DUAL) s " +
                "ON (t.PTNRKY=s.PTNRKY AND t.PTNRTY=s.PTNRTY AND t.OWNRKY=s.OWNRKY) " +
                "WHEN MATCHED THEN UPDATE SET " + setClause + "t.LMODAT=?, t.LMOTIM=?, t.LMOUSR='DCON_SET' " +
                "WHEN NOT MATCHED THEN INSERT (PTNRKY,PTNRTY,OWNRKY,WAREKY," + insCols + "LMODAT,LMOTIM,LMOUSR) " +
                "VALUES (?,?,?,?," + insVals + "?,?,'DCON_SET')";

            for (Map<String, Object> it : items) {
                String ptnrky = str(it.get("ptnrky"));
                String ptnrty = Objects.toString(it.get("ptnrty"), "CT").trim();
                String ownrky = Objects.toString(it.get("ownrky"), "KN").trim();
                String wareky = Objects.toString(it.getOrDefault("wareky", "W001"), "W001").trim();
                if (ptnrky.isBlank()) continue;

                // 스코프 납품처(PTNL01=?) 대상인지 검증
                int psCount = tmsJdbc.queryForObject(
                    "SELECT COUNT(*) FROM KNRAWMS.BZPTN WHERE PTNRKY=? AND PTNRTY='CT' AND PTNL01=?",
                    Integer.class, ptnrky, ptnl01
                );
                if (psCount == 0) { saved++; continue; }

                // 각 컬럼 값 추출 (소문자 키 우선, 없으면 원본 키)
                //   ⚠️ 빈 문자열/미설정은 NULL 로 저장하되, raw null 바인딩은 ORA-17004(열 유형
                //      부적합)를 유발하므로 vc()(SqlParameterValue VARCHAR)로 감싼다. VARCHAR NULL
                //      은 Oracle 이 대상 컬럼 타입(VARCHAR/NUMBER)으로 안전하게 변환/저장한다.
                Object[] colVals = new Object[columnNames.length];
                for (int i = 0; i < columnNames.length; i++) {
                    Object v = it.get(columnNames[i].toLowerCase());
                    if (v == null) v = it.get(columnNames[i]);
                    if (v instanceof String && ((String) v).isBlank()) v = null;
                    colVals[i] = vc(v);   // null-safe 바인딩
                }

                // 파라미터 순서: USING(3) + UPDATE(cols + lmodat + lmotim) + INSERT(ptnrky,ptnrty,ownrky,wareky + cols + lmodat + lmotim)
                List<Object> params = new ArrayList<>();
                params.add(ptnrky); params.add(ptnrty); params.add(ownrky);          // USING
                for (Object cv : colVals) params.add(cv);                              // UPDATE SET cols
                params.add(lmodat); params.add(lmotim);                               // UPDATE lmodat/lmotim
                params.add(ptnrky); params.add(ptnrty); params.add(ownrky); params.add(wareky); // INSERT keys
                for (Object cv : colVals) params.add(cv);                              // INSERT cols
                params.add(lmodat); params.add(lmotim);                               // INSERT lmodat/lmotim

                tmsJdbc.update(sql, params.toArray());
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

    // ══════════════════════════════════════════════════════════════
    //  제약조건 항목 관리 (/api/const-item/*)
    //  대상 테이블: TMS_DS_DISPATCH_CONST (마스터 제약조건 목록)
    //              TMS_DS_DISPATCH_CONST_SET_ITEM (세트별 설정값)
    // ══════════════════════════════════════════════════════════════

    /**
     * 전체 제약조건 목록 조회.
     * set_id 지정 시 해당 세트의 USE_YN / PARAM_VALUE 도 함께 반환.
     */
    public Map<String, Object> constItemList(Integer setId) {
        try {
            List<Map<String, Object>> rows;
            if (setId != null) {
                // 마스터 + 세트 설정값 LEFT JOIN
                rows = tmsJdbc.queryForList(
                    "SELECT c.CONST_ID, c.PROFILE_ID, c.CONST_TYPE, c.CONST_KEY, " +
                    "       c.CONST_OP, c.CONST_VALUE, c.TARGET_ID, c.TARGET_NM, " +
                    "       c.NOTE, c.ACTIVE_YN, c.SORT_SEQ, " +
                    "       p.PROFILE_NM, " +
                    "       i.ITEM_ID, i.ACTIVE_YN AS USE_YN, i.PARAM_VALUE AS SETTING_VAL " +
                    "FROM KNRAWMS.TMS_DS_DISPATCH_CONST c " +
                    "LEFT JOIN KNRAWMS.TMS_DS_DISPATCH_PROFILE p ON p.PROFILE_ID = c.PROFILE_ID " +
                    "LEFT JOIN KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM i " +
                    "       ON i.CONST_ID = c.CONST_ID AND i.SET_ID = ? " +
                    "ORDER BY c.CONST_TYPE, c.SORT_SEQ, c.CONST_ID",
                    setId
                );
            } else {
                rows = tmsJdbc.queryForList(
                    "SELECT c.CONST_ID, c.PROFILE_ID, c.CONST_TYPE, c.CONST_KEY, " +
                    "       c.CONST_OP, c.CONST_VALUE, c.TARGET_ID, c.TARGET_NM, " +
                    "       c.NOTE, c.ACTIVE_YN, c.SORT_SEQ, " +
                    "       p.PROFILE_NM " +
                    "FROM KNRAWMS.TMS_DS_DISPATCH_CONST c " +
                    "LEFT JOIN KNRAWMS.TMS_DS_DISPATCH_PROFILE p ON p.PROFILE_ID = c.PROFILE_ID " +
                    "ORDER BY c.CONST_TYPE, c.SORT_SEQ, c.CONST_ID"
                );
            }
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) { return errMap(e); }
    }

    /**
     * 제약조건 항목 저장 (INSERT or UPDATE TMS_DS_DISPATCH_CONST).
     * const_id 없으면 INSERT, 있으면 UPDATE.
     */
    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> constItemSave(Map<String, Object> body) {
        try {
            Long constId    = toLong(body.get("const_id"));
            String constType = str(body.getOrDefault("const_type", "GLOBAL"));
            String constKey  = str(body.get("const_key")).toUpperCase();
            String constOp   = str(body.getOrDefault("const_op", "="));
            String constValue= str(body.get("const_value"));
            String targetId  = str(body.get("target_id"));
            String targetNm  = str(body.get("target_nm"));
            String note      = str(body.get("note"));
            String activeYn  = str(body.getOrDefault("active_yn", "Y"));
            int sortSeq      = toInt(body.get("sort_seq"), 0);

            if (constKey.isBlank()) return Map.of("ok", false, "error", "CONST_KEY 필수");

            // PROFILE_ID: 명시 없으면 첫 번째 프로파일 사용
            Long profileId = toLong(body.get("profile_id"));
            if (profileId == null) {
                List<Map<String, Object>> pr = tmsJdbc.queryForList(
                    "SELECT PROFILE_ID FROM KNRAWMS.TMS_DS_DISPATCH_PROFILE ORDER BY PROFILE_ID FETCH FIRST 1 ROWS ONLY");
                profileId = pr.isEmpty() ? 1L : toLong(pr.get(0).get("PROFILE_ID"));
            }

            if (constId != null) {
                // UPDATE
                tmsJdbc.update(
                    "UPDATE KNRAWMS.TMS_DS_DISPATCH_CONST SET CONST_TYPE=?,CONST_KEY=?,CONST_OP=?,CONST_VALUE=?," +
                    "TARGET_ID=?,TARGET_NM=?,NOTE=?,ACTIVE_YN=?,SORT_SEQ=?,LMODAT=? WHERE CONST_ID=?",
                    constType, constKey, constOp, constValue,
                    targetId, targetNm, note, activeYn, sortSeq, today(), constId);
            } else {
                // INSERT — nextConstId(): 시퀀스 존재 시 NEXTVAL, 없으면 MAX+1 폴백
                constId = nextConstId();
                tmsJdbc.update(
                    "INSERT INTO KNRAWMS.TMS_DS_DISPATCH_CONST " +
                    "(CONST_ID,PROFILE_ID,CONST_TYPE,CONST_KEY,CONST_VALUE,CONST_OP," +
                    "TARGET_ID,TARGET_NM,ACTIVE_YN,NOTE,SORT_SEQ,CREDAT,LMODAT) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    constId, profileId, constType, constKey, constValue, constOp,
                    targetId, targetNm, activeYn, note, sortSeq, today(), today());
            }
            return Map.of("ok", true, "CONST_ID", constId);
        } catch (Exception e) { return errMap(e); }
    }

    /**
     * 제약조건 항목 삭제.
     * TMS_DS_DISPATCH_CONST_SET_ITEM 연관 행도 함께 삭제.
     */
    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> constItemDelete(Map<String, Object> body) {
        Long constId = toLong(body.get("const_id"));
        if (constId == null) return Map.of("ok", false, "error", "const_id 필수");
        try {
            tmsJdbc.update("DELETE FROM KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM WHERE CONST_ID=?", constId);
            int del = tmsJdbc.update("DELETE FROM KNRAWMS.TMS_DS_DISPATCH_CONST WHERE CONST_ID=?", constId);
            return Map.of("ok", true, "deleted", del);
        } catch (Exception e) { return errMap(e); }
    }

    /**
     * 세트별 제약조건 설정값 저장 (USE_YN / PARAM_VALUE).
     * TMS_DS_DISPATCH_CONST_SET_ITEM MERGE (있으면 UPDATE, 없으면 INSERT).
     */
    @Transactional(transactionManager = "tmsTransactionManager")
    public Map<String, Object> constItemSettingSave(Map<String, Object> body) {
        Integer setId = toInteger(body.get("set_id"));
        if (setId == null) return Map.of("ok", false, "error", "set_id 필수");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> settings = (List<Map<String, Object>>) body.get("settings");
        if (settings == null || settings.isEmpty()) return Map.of("ok", true, "saved", 0);

        try {
            Long nextItemId = tmsJdbc.queryForObject(
                "SELECT NVL(MAX(ITEM_ID),0)+1 FROM KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM", Long.class);
            int saved = 0;
            for (Map<String, Object> s : settings) {
                Long constId  = toLong(s.get("const_id"));
                String useYn  = str(s.getOrDefault("use_yn", "N"));
                String paramVal = str(s.get("setting_val"));
                String note   = str(s.get("note"));
                if (constId == null) continue;

                // 이미 ITEM_ID 있으면 UPDATE, 없으면 INSERT
                List<Map<String, Object>> existing = tmsJdbc.queryForList(
                    "SELECT ITEM_ID FROM KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM WHERE SET_ID=? AND CONST_ID=?",
                    setId, constId);
                if (!existing.isEmpty()) {
                    tmsJdbc.update(
                        "UPDATE KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM SET ACTIVE_YN=?,PARAM_VALUE=? WHERE SET_ID=? AND CONST_ID=?",
                        useYn, vc(paramVal.isEmpty() ? null : paramVal), setId, constId);
                } else {
                    tmsJdbc.update(
                        "INSERT INTO KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM (ITEM_ID,SET_ID,CONST_ID,ACTIVE_YN,PARAM_VALUE) VALUES (?,?,?,?,?)",
                        nextItemId++, setId, constId, useYn, vc(paramVal.isEmpty() ? null : paramVal));
                }
                saved++;
            }
            return Map.of("ok", true, "saved", saved);
        } catch (Exception e) { return errMap(e); }
    }

    private Map<String, Object> errMap(Exception e) {
        log.error("DispatchConfigApiService error: {}", e.getMessage(), e);
        return Map.of("ok", false, "error", e.getMessage());
    }

    /**
     * Oracle null 바인딩 시 SQL 타입 미지정으로 ORA-17004(부적합한 열 유형)가 발생하는 것을 방지.
     * VARCHAR 컬럼(PARAM_VALUE 등)에 null 을 안전하게 바인딩하기 위해 SqlParameterValue 로 감싼다.
     */
    private Object vc(Object v) {
        return new SqlParameterValue(Types.VARCHAR, v == null ? null : v.toString());
    }
    private String str(Object v) { return v == null ? "" : v.toString().trim(); }
    private Long toLong(Object v) { try { return v == null ? null : Long.valueOf(v.toString()); } catch (Exception e) { return null; } }
    private Integer toInteger(Object v) { try { return v == null ? null : Integer.valueOf(v.toString()); } catch (Exception e) { return null; } }
    private int toInt(Object v, int def) { try { return v == null ? def : Integer.parseInt(v.toString()); } catch (Exception e) { return def; } }
}
