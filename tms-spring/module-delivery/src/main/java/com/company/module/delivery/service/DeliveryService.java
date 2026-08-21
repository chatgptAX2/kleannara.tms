package com.company.module.delivery.service;

import com.company.module.delivery.dto.*;
import com.company.module.delivery.entity.wms.BzptnDetail;
import com.company.module.delivery.entity.tms.RouteCost;
import com.company.module.delivery.repository.wms.BzptnDetailRepository;
import com.company.module.delivery.repository.tms.RouteCostRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 납품처 관리 서비스
 * Flask: api_delivery_list / api_delivery_detail / api_delivery_save / api_delivery_delete
 *        api_route_cost_search / api_route_cost_pivot 대응
 *
 * ─ DB 라우팅 ───────────────────────────────────────────────────
 *   TMS/WMS DataSource 는 동일 Oracle DB (KNMESWMS) / 동일 계정 (KNRATMS).
 *   tmsEm (tmsPU) 단독으로 BZPTN JOIN BZPTN_DETAIL 직접 수행 가능.
 *   getList() 는 wmsJdbc/tmsJdbc 미사용 — tmsEm NativeQuery 로 통일
 * ───────────────────────────────────────────────────────────────
 */
@Slf4j
@Service
@Transactional(readOnly = true, transactionManager = "tmsTransactionManager")
public class DeliveryService {

    private final BzptnDetailRepository bzptnDetailRepo;
    private final RouteCostRepository   routeCostRepo;
    /** WMS Oracle — CMCDV(공통코드) 조회 전용 */
    private final JdbcTemplate          wmsJdbc;

    /** TMS DB (Oracle KNRATMS) — BZPTN / BZPTN_DETAIL 읽기·쓰기용 */
    @PersistenceContext(unitName = "tmsPU")
    private EntityManager tmsEm;

    public DeliveryService(
            BzptnDetailRepository bzptnDetailRepo,
            RouteCostRepository   routeCostRepo,
            @Qualifier("wmsJdbcTemplate") JdbcTemplate wmsJdbc) {
        this.bzptnDetailRepo = bzptnDetailRepo;
        this.routeCostRepo   = routeCostRepo;
        this.wmsJdbc         = wmsJdbc;
    }

    /* ── 런타임 컬럼 존재 감지 (ORA-00904 방지) ───────────────────────────────
       운영 DB에 아직 선택적 컬럼 추가 SQL(FIX_BZPTN_DETAIL_ADD_*.sql)이
       적용되지 않았을 수 있어, 런타임에 KNRAWMS.BZPTN_DETAIL 컬럼 존재를
       감지하여 SELECT/정렬/저장 SQL 을 자동 적응시킨다(ORA-00904 방지).
       DocumentService.hasOpDateColumn() 패턴을 범용화. */
    private final java.util.concurrent.ConcurrentHashMap<String, Boolean> colExistsCache
        = new java.util.concurrent.ConcurrentHashMap<>();

    /** KNRAWMS.BZPTN_DETAIL 에 해당 컬럼이 존재하는지 감지(캐싱). 실패 시 미존재로 간주. */
    private boolean hasCol(String colName) {
        Boolean cached = colExistsCache.get(colName);
        if (cached != null) return cached;
        boolean exists = false;
        try {
            Object cnt = tmsEm.createNativeQuery(
                "SELECT COUNT(*) FROM ALL_TAB_COLUMNS " +
                "WHERE OWNER='KNRAWMS' AND TABLE_NAME='BZPTN_DETAIL' AND COLUMN_NAME=:col")
                .setParameter("col", colName)
                .getSingleResult();
            long n = cnt == null ? 0L
                : cnt instanceof BigDecimal ? ((BigDecimal) cnt).longValue()
                : ((Number) cnt).longValue();
            exists = n > 0;
        } catch (Exception e) {
            /* ALL_TAB_COLUMNS 조회 실패(권한/비Oracle 등) 시 안전하게 미존재로 간주 */
            log.warn("hasCol({}) detect failed, assume absent: {}", colName, e.getMessage());
            exists = false;
        }
        colExistsCache.put(colName, exists);
        return exists;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 납품처 목록 (Flask api_delivery_list)
    // tmsEm NativeQuery 사용 — JdbcTemplate DataSource 의존성 없애고 tmsEm 단일화
    // ──────────────────────────────────────────────────────────────────────────
    public Map<String, Object> getList(DeliverySearchRequest req) {
        int page   = req.getPage() == null ? 1 : req.getPage();
        int size   = req.getSize() == null ? 50 : req.getSize();
        int offset = (page - 1) * size;

        String wareky    = nullIfBlank(req.getVstel());
        String itemGroup = nullIfBlank(req.getSkug05());
        String ptnrky    = nullIfBlank(req.getPtnrky());
        String q         = nullIfBlank(req.getQ());

        boolean hasMaxTon      = hasCol("MAX_TON");
        boolean hasDeadline    = hasCol("DEADLINE_TIME");
        boolean hasDynamicDist = hasCol("DYNAMIC_DIST_M");

        // ── 동적 WHERE 절 ──────────────────────────────────────────────
        StringBuilder where = new StringBuilder(" WHERE b.PTNRTY = 'CT'");

        if (wareky    != null) where.append(" AND d.WAREKY = :wareky");
        // 제품군: BZPTN.PTNL01(SAP 제품군 코드) 우선, BZPTN_DETAIL.ITEM_GROUP 병행 조건
        if (itemGroup != null) where.append(" AND (b.PTNL01 = :itemGroup OR d.ITEM_GROUP = :itemGroup)");
        if (ptnrky    != null) where.append(" AND (b.PTNRKY LIKE :ptnrky OR b.NAME01 LIKE :ptnrky)");
        if (q         != null) where.append(" AND (b.PTNRKY LIKE :q OR b.NAME01 LIKE :q OR b.ADDR01 LIKE :q OR b.REGN01 LIKE :q)");

        // ── 정렬 화이트리스트 (선택적 컬럼은 존재 시에만 허용) ────────
        Set<String> allowed = new HashSet<>(Arrays.asList(
            "PTNRKY","NAME01","WAREKY","ITEM_GROUP","ADDR01","AREA_CD","FORKLIFT_YN"));
        if (hasDeadline)    allowed.add("DEADLINE_TIME");
        if (hasMaxTon)      allowed.add("MAX_TON");
        if (hasDynamicDist) allowed.add("DYNAMIC_DIST_M");
        String sc = req.getSortCol() == null ? "PTNRKY" : req.getSortCol();
        if (!allowed.contains(sc)) sc = "PTNRKY";
        String sd = "DESC".equalsIgnoreCase(req.getSortDir()) ? "DESC" : "ASC";

        // ── COUNT ──────────────────────────────────────────────────────
        String countSql =
            "SELECT COUNT(*) FROM KNRAWMS.BZPTN b" +
            " LEFT JOIN KNRAWMS.BZPTN_DETAIL d" +
            "   ON b.PTNRKY=d.PTNRKY AND b.PTNRTY=d.PTNRTY AND b.OWNRKY=d.OWNRKY" +
            where;

        Query countQ = tmsEm.createNativeQuery(countSql);
        if (wareky    != null) countQ.setParameter("wareky",    wareky);
        if (itemGroup != null) countQ.setParameter("itemGroup", itemGroup);
        if (ptnrky    != null) countQ.setParameter("ptnrky",    "%" + ptnrky + "%");
        if (q         != null) countQ.setParameter("q",         "%" + q + "%");

        Object countResult = countQ.getSingleResult();
        long total = countResult == null ? 0L
            : countResult instanceof BigDecimal ? ((BigDecimal) countResult).longValue()
            : ((Number) countResult).longValue();

        // ── 데이터 (Oracle OFFSET/FETCH) ──────────────────────────────
        // 선택적 컬럼은 운영 DB에 존재할 때만 SELECT (미존재 시 NULL 상수로 대체 → ORA-00904 방지)
        String deadlineSelect = hasDeadline    ? "d.DEADLINE_TIME"  : "NULL AS DEADLINE_TIME";
        String maxTonSelect   = hasMaxTon      ? "d.MAX_TON"        : "NULL AS MAX_TON";
        String dynDistSelect  = hasDynamicDist ? "d.DYNAMIC_DIST_M" : "NULL AS DYNAMIC_DIST_M";
        String dataSql =
            "SELECT b.PTNRKY, b.NAME01, b.PTNRTY, b.OWNRKY," +
            "       b.ADDR01, b.ADDR02, b.REGN01, b.TELN01," +
            "       d.WAREKY, d.ROUTE_CD, d.ITEM_GROUP, d.AREA_CD," +
            "       d.UNLOAD_TIME, d.MAX_HEIGHT, d.AUTO_ALLOC_YN, d.FORKLIFT_YN," +
            "       d.INB_TIME_FROM1, d.INB_TIME_TO1, d.MAX_BOX_QTY, " + deadlineSelect + ", " + maxTonSelect + ", " + dynDistSelect + "," +
            "       CASE WHEN d.PTNRKY IS NOT NULL THEN 'Y' ELSE 'N' END AS HAS_DETAIL" +
            " FROM KNRAWMS.BZPTN b" +
            " LEFT JOIN KNRAWMS.BZPTN_DETAIL d" +
            "   ON b.PTNRKY=d.PTNRKY AND b.PTNRTY=d.PTNRTY AND b.OWNRKY=d.OWNRKY" +
            where +
            " ORDER BY " + sc + " " + sd +
            " OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY";

        Query dataQ = tmsEm.createNativeQuery(dataSql);
        if (wareky    != null) dataQ.setParameter("wareky",    wareky);
        if (itemGroup != null) dataQ.setParameter("itemGroup", itemGroup);
        if (ptnrky    != null) dataQ.setParameter("ptnrky",    "%" + ptnrky + "%");
        if (q         != null) dataQ.setParameter("q",         "%" + q + "%");
        dataQ.setParameter("offset", offset);
        dataQ.setParameter("size",   size);

        @SuppressWarnings("unchecked")
        List<Object[]> rawRows = dataQ.getResultList();

        // ── Object[] → Map (컬럼명 SELECT 순서에 맞게 매핑) ──────────
        String[] cols = {
            "PTNRKY","NAME01","PTNRTY","OWNRKY",
            "ADDR01","ADDR02","REGN01","TELN01",
            "WAREKY","ROUTE_CD","ITEM_GROUP","AREA_CD",
            "UNLOAD_TIME","MAX_HEIGHT","AUTO_ALLOC_YN","FORKLIFT_YN",
            "INB_TIME_FROM1","INB_TIME_TO1","MAX_BOX_QTY","DEADLINE_TIME","MAX_TON","DYNAMIC_DIST_M",
            "HAS_DETAIL"
        };
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object raw : rawRows) {
            Map<String, Object> m = new LinkedHashMap<>();
            if (raw instanceof Object[]) {
                Object[] arr = (Object[]) raw;
                for (int i = 0; i < cols.length && i < arr.length; i++) {
                    m.put(cols[i], arr[i] == null ? "" : arr[i].toString().strip());
                }
            } else {
                // 단일 컬럼인 경우(cols 1개) — 방어
                m.put(cols[0], raw == null ? "" : raw.toString().strip());
            }
            result.add(m);
        }

        log.debug("[DeliveryService.getList] total={} page={} size={} rows={}", total, page, size, result.size());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("page",  page);
        out.put("size",  size);
        out.put("pages", total > 0 ? (int) Math.ceil((double) total / size) : 1);
        out.put("rows",  result);
        return out;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 납품처 상세 (Flask api_delivery_detail)
    // KNRAWMS.BZPTN + KNRAWMS.BZPTN_DETAIL → tmsEm 단독 (동일 DB)
    // ──────────────────────────────────────────────────────────────────────────
    public Map<String, Object> getDetail(String ptnrky, String ptnrty, String ownrky) {
        @SuppressWarnings("unchecked")
        List<Object[]> bRows = tmsEm.createNativeQuery(
            "SELECT PTNRKY, NAME01, PTNRTY, OWNRKY, ADDR01, ADDR02, REGN01, TELN01" +
            " FROM KNRAWMS.BZPTN WHERE PTNRKY=? AND PTNRTY=? AND OWNRKY=?")
            .setParameter(1, ptnrky).setParameter(2, ptnrty).setParameter(3, ownrky)
            .getResultList();
        if (bRows.isEmpty()) throw new com.company.core.common.exception.EntityNotFoundException(
            com.company.core.common.exception.ErrorCode.DELIVERY_NOT_FOUND);

        Optional<BzptnDetail> detail = bzptnDetailRepo.findByPtnrkyAndPtnrtyAndOwnrky(ptnrky, ptnrty, ownrky);

        // Map.of()는 null 값 비허용(NPE) → LinkedHashMap 사용
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bzptn",  toBzptnMap(bRows.get(0)));
        out.put("detail", detail.map(this::detailToMap).orElse(null));
        return out;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 납품처 저장 (Flask api_delivery_save)
    // KNRAWMS.BZPTN_DETAIL 쓰기 → tmsEm (TMS Oracle)
    // ──────────────────────────────────────────────────────────────────────────
    @Transactional(transactionManager = "tmsTransactionManager")
    public String saveDetail(DeliverySaveRequest req) {
        String ptnrky = req.getPtnrky() == null ? "" : req.getPtnrky().strip();
        String ptnrty = req.getPtnrty() == null ? "CT" : req.getPtnrty().strip();
        String ownrky = req.getOwnrky() == null ? "KN" : req.getOwnrky().strip();
        if (ptnrky.isEmpty()) throw new IllegalArgumentException("납품처코드(PTNRKY)는 필수입니다");

        String nowdt = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String nowtm = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));

        boolean hasMaxTon      = hasCol("MAX_TON");
        boolean hasDeadline    = hasCol("DEADLINE_TIME");
        boolean hasDynamicDist = hasCol("DYNAMIC_DIST_M");

        if (bzptnDetailRepo.existsByPtnrkyAndPtnrtyAndOwnrky(ptnrky, ptnrty, ownrky)) {
            // 선택적 컬럼은 존재 시에만 SET 절 포함 (미존재 시 제외 → ORA-00904 방지)
            String deadlineSet = hasDeadline    ? "DEADLINE_TIME=?,"  : "";
            String maxTonSet   = hasMaxTon      ? "MAX_TON=?,"        : "";
            String dynDistSet  = hasDynamicDist ? "DYNAMIC_DIST_M=?," : "";
            Query q = tmsEm.createNativeQuery(
                "UPDATE KNRAWMS.BZPTN_DETAIL SET " +
                "  WAREKY=?,ROUTE_CD=?,ITEM_GROUP=?,UNLOAD_TIME=?," +
                "  INB_TIME_FROM1=?,INB_TIME_TO1=?,AREA_CD=?,MAX_HEIGHT=?," +
                "  FORKLIFT_YN=?,HANDWORK_YN=?,AUTO_PLT=?,MAX_BOX_QTY=?," +
                "  AUTO_ALLOC_YN=?,SINGLE_ITEM_YN=?,NY_TYPE=?,SINGLE_HEIGHT=?," +
                "  DYNAMIC_YN=?,LTL_YN=?,PRIORITY_YN=?,MIN_QTSIWH=?," +
                "  LATITUDE=?,LONGITUDE=?,DEL_YN=?," + deadlineSet + maxTonSet + dynDistSet +
                "  LMODAT=?,LMOTIM=?,LMOUSR=? " +
                "WHERE PTNRKY=? AND PTNRTY=? AND OWNRKY=?");
            int p = 1;
            q.setParameter(p++, req.getWareky())
             .setParameter(p++, req.getRouteCd())
             .setParameter(p++, req.getItemGroup())
             .setParameter(p++, req.getUnloadTime())
             .setParameter(p++, req.getInbTimeFrom1())
             .setParameter(p++, req.getInbTimeTo1())
             .setParameter(p++, req.getAreaCd())
             .setParameter(p++, req.getMaxHeight())
             .setParameter(p++, req.getForkliftYn())
             .setParameter(p++, req.getHandworkYn())
             .setParameter(p++, req.getAutoPlt())
             .setParameter(p++, req.getMaxBoxQty())
             .setParameter(p++, req.getAutoAllocYn())
             .setParameter(p++, req.getSingleItemYn())
             .setParameter(p++, req.getNyType())
             .setParameter(p++, req.getSingleHeight())
             .setParameter(p++, req.getDynamicYn())
             .setParameter(p++, req.getLtlYn())
             .setParameter(p++, req.getPriorityYn())
             .setParameter(p++, req.getMinQtsiwh())
             .setParameter(p++, req.getLatitude())
             .setParameter(p++, req.getLongitude())
             .setParameter(p++, req.getDelYn());
            if (hasDeadline)    q.setParameter(p++, req.getDeadlineTime());
            if (hasMaxTon)      q.setParameter(p++, req.getMaxTon());
            if (hasDynamicDist) q.setParameter(p++, req.getDynamicDistM());
            q.setParameter(p++, nowdt).setParameter(p++, nowtm).setParameter(p++, "WEB")
             .setParameter(p++, ptnrky).setParameter(p++, ptnrty).setParameter(p++, ownrky)
             .executeUpdate();
            return "updated";
        } else {
            // 선택적 컬럼은 존재 시에만 INSERT 컬럼/값 포함
            String deadlineCol = hasDeadline    ? "DEADLINE_TIME," : "";
            String maxTonCol   = hasMaxTon      ? "MAX_TON,"       : "";
            String dynDistCol  = hasDynamicDist ? "DYNAMIC_DIST_M,": "";
            String deadlineVal = hasDeadline    ? "?," : "";
            String maxTonVal   = hasMaxTon      ? "?," : "";
            String dynDistVal  = hasDynamicDist ? "?," : "";
            Query q = tmsEm.createNativeQuery(
                "INSERT INTO KNRAWMS.BZPTN_DETAIL " +
                "(PTNRKY,PTNRTY,OWNRKY,WAREKY,ROUTE_CD,ITEM_GROUP,UNLOAD_TIME," +
                " INB_TIME_FROM1,INB_TIME_TO1,AREA_CD,MAX_HEIGHT,FORKLIFT_YN,HANDWORK_YN," +
                " AUTO_PLT,MAX_BOX_QTY,AUTO_ALLOC_YN,SINGLE_ITEM_YN,NY_TYPE,SINGLE_HEIGHT," +
                " DYNAMIC_YN,LTL_YN,PRIORITY_YN,MIN_QTSIWH,LATITUDE,LONGITUDE,DEL_YN," +
                deadlineCol + maxTonCol + dynDistCol + "CREDAT,CRETIM,CREUSR,LMODAT,LMOTIM,LMOUSR) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?," +
                deadlineVal + maxTonVal + dynDistVal + "?,?,?,?,?,?)");
            int p = 1;
            q.setParameter(p++, ptnrky).setParameter(p++, ptnrty).setParameter(p++, ownrky)
             .setParameter(p++, req.getWareky())
             .setParameter(p++, req.getRouteCd())
             .setParameter(p++, req.getItemGroup())
             .setParameter(p++, req.getUnloadTime())
             .setParameter(p++, req.getInbTimeFrom1())
             .setParameter(p++, req.getInbTimeTo1())
             .setParameter(p++, req.getAreaCd())
             .setParameter(p++, req.getMaxHeight())
             .setParameter(p++, req.getForkliftYn())
             .setParameter(p++, req.getHandworkYn())
             .setParameter(p++, req.getAutoPlt())
             .setParameter(p++, req.getMaxBoxQty())
             .setParameter(p++, req.getAutoAllocYn())
             .setParameter(p++, req.getSingleItemYn())
             .setParameter(p++, req.getNyType())
             .setParameter(p++, req.getSingleHeight())
             .setParameter(p++, req.getDynamicYn())
             .setParameter(p++, req.getLtlYn())
             .setParameter(p++, req.getPriorityYn())
             .setParameter(p++, req.getMinQtsiwh())
             .setParameter(p++, req.getLatitude())
             .setParameter(p++, req.getLongitude())
             .setParameter(p++, req.getDelYn());
            if (hasDeadline)    q.setParameter(p++, req.getDeadlineTime());
            if (hasMaxTon)      q.setParameter(p++, req.getMaxTon());
            if (hasDynamicDist) q.setParameter(p++, req.getDynamicDistM());
            q.setParameter(p++, nowdt).setParameter(p++, nowtm).setParameter(p++, "WEB")
             .setParameter(p++, nowdt).setParameter(p++, nowtm).setParameter(p++, "WEB")
             .executeUpdate();
            return "created";
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 납품처 삭제 (Flask api_delivery_delete) — TMS Oracle DB
    // ──────────────────────────────────────────────────────────────────────────
    @Transactional(transactionManager = "tmsTransactionManager")
    public void deleteDetail(String ptnrky, String ptnrty, String ownrky) {
        String nowdt = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String nowtm = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        BzptnDetail d = bzptnDetailRepo.findByPtnrkyAndPtnrtyAndOwnrky(ptnrky, ptnrty, ownrky)
            .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(
                com.company.core.common.exception.ErrorCode.DELIVERY_NOT_FOUND));
        d.delete(nowdt, nowtm, "WEB");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 운송비 검색 (Flask api_route_cost_search)
    // 반환: { rows, total, carclasses }
    //   carclasses: KNRAWMS.CMCDV WHERE CMCDKY='TMS_CARCLASS10'
    //               → [{value:CMCDVL, label:CDESC1}, ...]
    // ──────────────────────────────────────────────────────────────────────────
    public Map<String, Object> searchRouteCost(String wareky, String ptnrky, String carclass) {
        // 1. ROUTE_COST 행 조회
        List<Object[]> rawRows = routeCostRepo.searchList(
            nullIfBlank(wareky), nullIfBlank(ptnrky), nullIfBlank(carclass)
        );

        // 2. Object[] → Map (alias 컬럼명: SHPPT,PTNRKY,CARCLASS,COST,DIST_KM,DATE_START,DATE_END,UNIT)
        //    nativeQuery Object[] 인덱스 순서: COST_ID(0),SHPPT(1),PTNRKY(2),CARCLASS(3),COST(4),DIST_KM(5),DATE_START(6),DATE_END(7),UNIT(8)
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] r : rawRows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("COST_ID",    r[0]);
            m.put("SHPPT",      str(r[1]));
            m.put("PTNRKY",     str(r[2]));
            m.put("CARCLASS",   str(r[3]));
            m.put("COST",       r[4]);
            m.put("DIST_KM",    r[5]);
            m.put("DATE_START", str(r[6]));
            m.put("DATE_END",   str(r[7]));
            m.put("UNIT",       str(r[8]));
            rows.add(m);
        }

        // 3. CMCDV TMS_CARCLASS10 → 차량톤수 select 옵션
        List<Map<String, Object>> carclasses = loadCarclasses();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows",       rows);
        result.put("total",      (long) rows.size());
        result.put("carclasses", carclasses);
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 운송비 피벗 (Flask api_route_cost_pivot)
    // 반환: { carclasses, rows, total }
    //   carclasses: ROUTE_COST의 실제 CARTYPE 목록 (CMCDV 기준)
    // ──────────────────────────────────────────────────────────────────────────
    public Map<String, Object> pivotRouteCost(String wareky, String ptnrky, String carclass) {
        List<Object[]> rawRows = routeCostRepo.searchList(nullIfBlank(wareky), nullIfBlank(ptnrky), nullIfBlank(carclass));

        // Object[] 인덱스: COST_ID(0),SHPPT(1),PTNRKY(2),CARCLASS(3),COST(4),DIST_KM(5),DATE_START(6),DATE_END(7),UNIT(8)
        List<String> carclassesList = new ArrayList<>();
        // pivotMap 키: PTNRKY → (CARCLASS → COST)
        Map<String, Map<String, Object>> pivotMap = new LinkedHashMap<>();
        // 행별 메타정보 보관 (SHPPT, DATE_START, DATE_END, UNIT)
        Map<String, Map<String, String>> metaMap = new LinkedHashMap<>();

        for (Object[] r : rawRows) {
            String shppt     = str(r[1]);
            String ptnrkyVal = str(r[2]);
            String cc        = str(r[3]);   // CARCLASS (= CARTYPE)
            Object cost      = r[4];        // COST_AMT
            String dateStart = str(r[6]);
            String dateEnd   = str(r[7]);
            String unit      = str(r[8]);

            if (!carclassesList.contains(cc)) carclassesList.add(cc);

            // 피벗 행 생성 (PTNRKY 기준)
            pivotMap.computeIfAbsent(ptnrkyVal, k -> new LinkedHashMap<>()).put(cc, cost);
            metaMap.computeIfAbsent(ptnrkyVal, k -> {
                Map<String,String> m = new LinkedHashMap<>();
                m.put("SHPPT", shppt); m.put("DATE_START", dateStart);
                m.put("DATE_END", dateEnd); m.put("UNIT", unit);
                return m;
            });
        }

        List<Map<String, Object>> pivotRows = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : pivotMap.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            Map<String, String> meta = metaMap.getOrDefault(e.getKey(), Map.of());
            row.put("SHPPT",      meta.getOrDefault("SHPPT",      ""));
            row.put("PTNRKY",     e.getKey());
            row.put("DATE_START", meta.getOrDefault("DATE_START", ""));
            row.put("DATE_END",   meta.getOrDefault("DATE_END",   ""));
            row.put("UNIT",       meta.getOrDefault("UNIT",       "KRW"));
            row.putAll(e.getValue()); // CARCLASS → COST 피벗 컬럼
            pivotRows.add(row);
        }

        // CMCDV carclasses (select 옵션용)
        List<Map<String, Object>> carclassesOpts = loadCarclasses();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("carclasses", carclassesList);   // 프론트 피벗 헤더용 (CARTYPE 값 목록)
        result.put("carclassOpts", carclassesOpts); // 프론트 select 옵션용
        result.put("rows",       pivotRows);
        result.put("total",      (long) pivotRows.size());
        return result;
    }

    // ── CMCDV TMS_CARCLASS10 → [{value, label}] ────────────────────────────
    private List<Map<String, Object>> loadCarclasses() {
        try {
            List<Map<String, Object>> rows = wmsJdbc.queryForList(
                "SELECT CMCDVL AS value, CDESC1 AS label " +
                "FROM KNRAWMS.CMCDV WHERE CMCDKY = 'TMS_CARCLASS10' ORDER BY CMCDVL"
            );
            return rows;
        } catch (Exception e) {
            return List.of();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // util
    // ──────────────────────────────────────────────────────────────────────────
    private String str(Object o)         { return o == null ? "" : o.toString().strip(); }
    private double toDouble(Object o)    { try { return Double.parseDouble(o.toString()); } catch (Exception e) { return 0.0; } }
    private String nullIfBlank(String s) { return (s == null || s.isBlank()) ? null : s.strip(); }

    private Map<String, Object> toBzptnMap(Object o) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (o instanceof Object[]) {
            // SELECT PTNRKY,NAME01,PTNRTY,OWNRKY,ADDR01,ADDR02,REGN01,TELN01 순서
            String[] cols = {"PTNRKY","NAME01","PTNRTY","OWNRKY","ADDR01","ADDR02","REGN01","TELN01"};
            Object[] arr = (Object[]) o;
            for (int i = 0; i < cols.length && i < arr.length; i++) {
                m.put(cols[i], arr[i] == null ? "" : arr[i].toString().strip());
            }
        } else if (o != null) {
            m.put("PTNRKY", o.toString());
        }
        return m;
    }

    private Map<String, Object> detailToMap(BzptnDetail d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("PTNRKY", d.getPtnrky()); m.put("PTNRTY", d.getPtnrty());
        m.put("OWNRKY", d.getOwnrky()); m.put("WAREKY", d.getWareky());
        m.put("ROUTE_CD", d.getRouteCd()); m.put("ITEM_GROUP", d.getItemGroup());
        m.put("AREA_CD", d.getAreaCd()); m.put("UNLOAD_TIME", d.getUnloadTime());
        m.put("MAX_HEIGHT", d.getMaxHeight()); m.put("AUTO_ALLOC_YN", d.getAutoAllocYn());
        m.put("FORKLIFT_YN", d.getForkliftYn()); m.put("INB_TIME_FROM1", d.getInbTimeFrom1());
        m.put("INB_TIME_TO1", d.getInbTimeTo1()); m.put("MAX_BOX_QTY", d.getMaxBoxQty());
        m.put("DEADLINE_TIME", d.getDeadlineTime()); m.put("MAX_TON", d.getMaxTon());
        m.put("DYNAMIC_DIST_M", d.getDynamicDistM());
        m.put("DEL_YN", d.getDelYn()); m.put("LATITUDE", d.getLatitude()); m.put("LONGITUDE", d.getLongitude());
        return m;
    }
}
