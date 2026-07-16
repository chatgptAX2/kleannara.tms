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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
 * ───────────────────────────────────────────────────────────────
 */
@Service
@Transactional(readOnly = true, transactionManager = "tmsTransactionManager")
public class DeliveryService {

    private final BzptnDetailRepository bzptnDetailRepo;
    private final RouteCostRepository   routeCostRepo;
    /** WMS Oracle — CMCDV 공통코드 조회용 (KNRAWMS.CMCDV) */
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

    // ──────────────────────────────────────────────────────────────────────────
    // 납품처 목록 (Flask api_delivery_list)
    // BZPTN JOIN BZPTN_DETAIL — 동일 DB이므로 단일 쿼리로 처리
    // ──────────────────────────────────────────────────────────────────────────
    public Map<String, Object> getList(DeliverySearchRequest req) {
        int page   = req.getPage() == null ? 1 : req.getPage();
        int size   = req.getSize() == null ? 50 : req.getSize();
        int offset = (page - 1) * size;

        String wareky    = nullIfBlank(req.getVstel());
        String itemGroup = nullIfBlank(req.getSkug05());
        String ptnrky    = nullIfBlank(req.getPtnrky());
        String q         = nullIfBlank(req.getQ());

        // JOIN 쿼리 단일 호출 (BZPTN + BZPTN_DETAIL)
        long total = bzptnDetailRepo.searchCount(wareky, itemGroup, ptnrky, q);
        List<Object[]> rows = bzptnDetailRepo.searchList(wareky, itemGroup, ptnrky, q, size, offset);

        // row → Map 변환
        // index: 0=PTNRKY, 1=NAME01, 2=PTNRTY, 3=OWNRKY, 4=ADDR01, 5=ADDR02,
        //        6=REGN01, 7=TELN01, 8=WAREKY, 9=ROUTE_CD, 10=ITEM_GROUP, 11=AREA_CD,
        //        12=UNLOAD_TIME, 13=MAX_HEIGHT, 14=AUTO_ALLOC_YN, 15=FORKLIFT_YN,
        //        16=INB_TIME_FROM1, 17=INB_TIME_TO1, 18=MAX_BOX_QTY, 19=DEADLINE_TIME,
        //        20=MAX_TON, 21=HAS_DETAIL
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("PTNRKY",         str(r[0]));
            m.put("NAME01",         str(r[1]));
            m.put("PTNRTY",         str(r[2]));
            m.put("OWNRKY",         str(r[3]));
            m.put("ADDR01",         str(r[4]));
            m.put("ADDR02",         str(r[5]));
            m.put("REGN01",         str(r[6]));
            m.put("TELN01",         str(r[7]));
            m.put("WAREKY",         str(r[8]));
            m.put("ROUTE_CD",       str(r[9]));
            m.put("ITEM_GROUP",     str(r[10]));
            m.put("AREA_CD",        str(r[11]));
            m.put("UNLOAD_TIME",    r[12]);
            m.put("MAX_HEIGHT",     r[13]);
            m.put("AUTO_ALLOC_YN",  str(r[14]));
            m.put("FORKLIFT_YN",    str(r[15]));
            m.put("INB_TIME_FROM1", str(r[16]));
            m.put("INB_TIME_TO1",   str(r[17]));
            m.put("MAX_BOX_QTY",    r[18]);
            m.put("DEADLINE_TIME",  str(r[19]));
            m.put("MAX_TON",        r[20]);
            m.put("HAS_DETAIL",     str(r[21]));
            result.add(m);
        }

        return Map.of(
            "total", total,
            "page", page, "size", size,
            "pages", total > 0 ? (int) Math.ceil((double) total / size) : 1,
            "rows", result
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 납품처 상세 (Flask api_delivery_detail)
    // KNRAWMS.BZPTN + KNRAWMS.BZPTN_DETAIL → tmsEm 단독 (동일 DB)
    // ──────────────────────────────────────────────────────────────────────────
    public Map<String, Object> getDetail(String ptnrky, String ptnrty, String ownrky) {
        @SuppressWarnings("unchecked")
        List<Object[]> bRows = tmsEm.createNativeQuery(
            "SELECT * FROM KNRAWMS.BZPTN WHERE PTNRKY=? AND PTNRTY=? AND OWNRKY=?")
            .setParameter(1, ptnrky).setParameter(2, ptnrty).setParameter(3, ownrky)
            .getResultList();
        if (bRows.isEmpty()) throw new com.company.core.common.exception.EntityNotFoundException(
            com.company.core.common.exception.ErrorCode.DELIVERY_NOT_FOUND);

        Optional<BzptnDetail> detail = bzptnDetailRepo.findByPtnrkyAndPtnrtyAndOwnrky(ptnrky, ptnrty, ownrky);
        return Map.of(
            "bzptn",  toMap(bRows.get(0)),
            "detail", detail.map(this::detailToMap).orElse(null)
        );
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

        if (bzptnDetailRepo.existsByPtnrkyAndPtnrtyAndOwnrky(ptnrky, ptnrty, ownrky)) {
            tmsEm.createNativeQuery("""
                UPDATE KNRAWMS.BZPTN_DETAIL SET
                  WAREKY=?,ROUTE_CD=?,ITEM_GROUP=?,UNLOAD_TIME=?,
                  INB_TIME_FROM1=?,INB_TIME_TO1=?,AREA_CD=?,MAX_HEIGHT=?,
                  FORKLIFT_YN=?,HANDWORK_YN=?,AUTO_PLT=?,MAX_BOX_QTY=?,
                  AUTO_ALLOC_YN=?,SINGLE_ITEM_YN=?,NY_TYPE=?,SINGLE_HEIGHT=?,
                  DYNAMIC_YN=?,LTL_YN=?,PRIORITY_YN=?,MIN_QTSIWH=?,
                  LATITUDE=?,LONGITUDE=?,DEL_YN=?,DEADLINE_TIME=?,MAX_TON=?,
                  LMODAT=?,LMOTIM=?,LMOUSR=?
                WHERE PTNRKY=? AND PTNRTY=? AND OWNRKY=?
                """)
              .setParameter(1,  req.getWareky())
              .setParameter(2,  req.getRouteCd())
              .setParameter(3,  req.getItemGroup())
              .setParameter(4,  req.getUnloadTime())
              .setParameter(5,  req.getInbTimeFrom1())
              .setParameter(6,  req.getInbTimeTo1())
              .setParameter(7,  req.getAreaCd())
              .setParameter(8,  req.getMaxHeight())
              .setParameter(9,  req.getForkliftYn())
              .setParameter(10, req.getHandworkYn())
              .setParameter(11, req.getAutoPlt())
              .setParameter(12, req.getMaxBoxQty())
              .setParameter(13, req.getAutoAllocYn())
              .setParameter(14, req.getSingleItemYn())
              .setParameter(15, req.getNyType())
              .setParameter(16, req.getSingleHeight())
              .setParameter(17, req.getDynamicYn())
              .setParameter(18, req.getLtlYn())
              .setParameter(19, req.getPriorityYn())
              .setParameter(20, req.getMinQtsiwh())
              .setParameter(21, req.getLatitude())
              .setParameter(22, req.getLongitude())
              .setParameter(23, req.getDelYn())
              .setParameter(24, req.getDeadlineTime())
              .setParameter(25, req.getMaxTon())
              .setParameter(26, nowdt).setParameter(27, nowtm).setParameter(28, "WEB")
              .setParameter(29, ptnrky).setParameter(30, ptnrty).setParameter(31, ownrky)
              .executeUpdate();
            return "updated";
        } else {
            tmsEm.createNativeQuery("""
                INSERT INTO KNRAWMS.BZPTN_DETAIL
                (PTNRKY,PTNRTY,OWNRKY,WAREKY,ROUTE_CD,ITEM_GROUP,UNLOAD_TIME,
                 INB_TIME_FROM1,INB_TIME_TO1,AREA_CD,MAX_HEIGHT,FORKLIFT_YN,HANDWORK_YN,
                 AUTO_PLT,MAX_BOX_QTY,AUTO_ALLOC_YN,SINGLE_ITEM_YN,NY_TYPE,SINGLE_HEIGHT,
                 DYNAMIC_YN,LTL_YN,PRIORITY_YN,MIN_QTSIWH,LATITUDE,LONGITUDE,DEL_YN,
                 DEADLINE_TIME,MAX_TON,CREDAT,CRETIM,CREUSR,LMODAT,LMOTIM,LMOUSR)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)
              .setParameter(1,  ptnrky).setParameter(2, ptnrty).setParameter(3, ownrky)
              .setParameter(4,  req.getWareky())
              .setParameter(5,  req.getRouteCd())
              .setParameter(6,  req.getItemGroup())
              .setParameter(7,  req.getUnloadTime())
              .setParameter(8,  req.getInbTimeFrom1())
              .setParameter(9,  req.getInbTimeTo1())
              .setParameter(10, req.getAreaCd())
              .setParameter(11, req.getMaxHeight())
              .setParameter(12, req.getForkliftYn())
              .setParameter(13, req.getHandworkYn())
              .setParameter(14, req.getAutoPlt())
              .setParameter(15, req.getMaxBoxQty())
              .setParameter(16, req.getAutoAllocYn())
              .setParameter(17, req.getSingleItemYn())
              .setParameter(18, req.getNyType())
              .setParameter(19, req.getSingleHeight())
              .setParameter(20, req.getDynamicYn())
              .setParameter(21, req.getLtlYn())
              .setParameter(22, req.getPriorityYn())
              .setParameter(23, req.getMinQtsiwh())
              .setParameter(24, req.getLatitude())
              .setParameter(25, req.getLongitude())
              .setParameter(26, req.getDelYn())
              .setParameter(27, req.getDeadlineTime())
              .setParameter(28, req.getMaxTon())
              .setParameter(29, nowdt).setParameter(30, nowtm).setParameter(31, "WEB")
              .setParameter(32, nowdt).setParameter(33, nowtm).setParameter(34, "WEB")
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

    private Map<String, Object> toMap(Object o) {
        if (o == null) return Map.of();
        return Map.of("raw", o.toString());
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
        m.put("DEL_YN", d.getDelYn()); m.put("LATITUDE", d.getLatitude()); m.put("LONGITUDE", d.getLongitude());
        return m;
    }
}
