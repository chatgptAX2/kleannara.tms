package com.company.module.delivery.service;

import com.company.module.delivery.dto.*;
import com.company.module.delivery.entity.wms.BzptnDetail;
import com.company.module.delivery.entity.tms.RouteCost;
import com.company.module.delivery.repository.wms.BzptnDetailRepository;
import com.company.module.delivery.repository.tms.RouteCostRepository;
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
 *   tmsEm  (tmsPU / Oracle TMS) :
 *     - BzptnDetailRepository (KNRAWMS.BZPTN_DETAIL)  -- TMS Oracle 테이블
 *     - em.createNativeQuery  KNRAWMS.BZPTN_DETAIL UPDATE/INSERT
 *   wmsEm  (wmsPU / Oracle WMS) :
 *     - KNRAWMS.BZPTN 원장 조회 (NAME01, ADDR01, REGN01, TELN01 등)
 *
 * ■ 2-step 패턴 (Cross-DB JOIN 불가)
 *   Step 1: wmsEm  → KNRAWMS.BZPTN  (WMS DB) 조회
 *   Step 2: tmsEm  → KNRAWMS.BZPTN_DETAIL (TMS DB) 조회
 *   Java에서 PTNRKY 기준으로 merge
 * ───────────────────────────────────────────────────────────────
 */
@Service
@Transactional(readOnly = true, transactionManager = "tmsTransactionManager")
public class DeliveryService {

    private final BzptnDetailRepository bzptnDetailRepo;
    private final RouteCostRepository   routeCostRepo;

    /** TMS DB (Oracle) — KNRAWMS.BZPTN_DETAIL 읽기/쓰기용 */
    @PersistenceContext(unitName = "tmsPU")
    private EntityManager tmsEm;

    /** WMS DB (Oracle) — KNRAWMS.BZPTN 원장 읽기용 */
    @PersistenceContext(unitName = "wmsPU")
    private EntityManager wmsEm;

    public DeliveryService(
            BzptnDetailRepository bzptnDetailRepo,
            RouteCostRepository   routeCostRepo) {
        this.bzptnDetailRepo = bzptnDetailRepo;
        this.routeCostRepo   = routeCostRepo;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 납품처 목록 (Flask api_delivery_list)
    // 2-step: tmsEm(BZPTN_DETAIL) + wmsEm(BZPTN) → Java merge
    // ──────────────────────────────────────────────────────────────────────────
    public Map<String, Object> getList(DeliverySearchRequest req) {
        int page   = req.getPage() == null ? 1 : req.getPage();
        int size   = req.getSize() == null ? 50 : req.getSize();
        int offset = (page - 1) * size;

        String wareky    = nullIfBlank(req.getVstel());
        String itemGroup = nullIfBlank(req.getSkug05());
        String ptnrky    = nullIfBlank(req.getPtnrky());
        String q         = nullIfBlank(req.getQ());

        // Step 1: tmsEm — BZPTN_DETAIL 조회 (wareky, itemGroup 필터)
        long total = bzptnDetailRepo.searchCount(wareky, itemGroup);
        List<Object[]> detailRows = bzptnDetailRepo.searchList(wareky, itemGroup, size, offset);

        // Step 2: wmsEm — BZPTN 일괄 조회 (NAME01, ADDR01, REGN01, TELN01)
        List<String> ptnrkyList = new ArrayList<>();
        for (Object[] r : detailRows) ptnrkyList.add(str(r[0]));

        Map<String, Object[]> bzptnMap = new LinkedHashMap<>();
        if (!ptnrkyList.isEmpty()) {
            String inClause = String.join(",", Collections.nCopies(ptnrkyList.size(), "?"));
            @SuppressWarnings("unchecked")
            List<Object[]> bzptnRows = wmsEm.createNativeQuery(
                "SELECT PTNRKY, NAME01, PTNRTY, OWNRKY, ADDR01, ADDR02, REGN01, TELN01 " +
                "FROM KNRAWMS.BZPTN WHERE PTNRKY IN (" + inClause + ") AND PTNRTY='CT'")
                .getResultList();
            // positional parameter 방식으로 설정
            // ※ JPA nativeQuery에서 IN절 바인딩은 직접 처리
            bzptnRows = new ArrayList<>();
            for (int i = 0; i < ptnrkyList.size(); i++) {
                String pk = ptnrkyList.get(i);
                @SuppressWarnings("unchecked")
                List<Object[]> single = wmsEm.createNativeQuery(
                    "SELECT PTNRKY, NAME01, PTNRTY, OWNRKY, ADDR01, ADDR02, REGN01, TELN01 " +
                    "FROM KNRAWMS.BZPTN WHERE PTNRKY=? AND PTNRTY='CT'")
                    .setParameter(1, pk)
                    .getResultList();
                bzptnRows.addAll(single);
            }
            for (Object[] b : bzptnRows) bzptnMap.put(str(b[0]), b);
        }

        // ptnrky/q 검색 필터 (Java 레벨)
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] r : detailRows) {
            String pk    = str(r[0]);
            Object[] bz  = bzptnMap.get(pk);
            String name01 = bz != null ? str(bz[1]) : "";
            String addr01 = bz != null ? str(bz[4]) : "";
            String regn01 = bz != null ? str(bz[6]) : "";

            // ptnrky / q 검색 필터 (BZPTN 컬럼 대상)
            if (ptnrky != null && !pk.contains(ptnrky) && !name01.contains(ptnrky)) continue;
            if (q != null && !pk.contains(q) && !name01.contains(q)
                          && !addr01.contains(q) && !regn01.contains(q)) continue;

            Map<String, Object> m = new LinkedHashMap<>();
            // BZPTN 원장 컬럼
            m.put("PTNRKY",   pk);
            m.put("NAME01",   name01);
            m.put("PTNRTY",   bz != null ? str(bz[2]) : "CT");
            m.put("OWNRKY",   bz != null ? str(bz[3]) : "");
            m.put("ADDR01",   addr01);
            m.put("ADDR02",   bz != null ? str(bz[5]) : "");
            m.put("REGN01",   regn01);
            m.put("TELN01",   bz != null ? str(bz[7]) : "");
            // BZPTN_DETAIL 컬럼 (index: 0=PTNRKY,1=PTNRTY,2=OWNRKY,3=WAREKY,4=ROUTE_CD,
            //                           5=ITEM_GROUP,6=AREA_CD,7=UNLOAD_TIME,8=MAX_HEIGHT,
            //                           9=AUTO_ALLOC_YN,10=FORKLIFT_YN,11=INB_TIME_FROM1,
            //                           12=INB_TIME_TO1,13=MAX_BOX_QTY,14=DEADLINE_TIME,15=MAX_TON)
            m.put("WAREKY",         str(r[3]));
            m.put("ROUTE_CD",       str(r[4]));
            m.put("ITEM_GROUP",     str(r[5]));
            m.put("AREA_CD",        str(r[6]));
            m.put("UNLOAD_TIME",    r[7]);
            m.put("MAX_HEIGHT",     r[8]);
            m.put("AUTO_ALLOC_YN",  str(r[9]));
            m.put("FORKLIFT_YN",    str(r[10]));
            m.put("INB_TIME_FROM1", str(r[11]));
            m.put("INB_TIME_TO1",   str(r[12]));
            m.put("MAX_BOX_QTY",    r[13]);
            m.put("DEADLINE_TIME",  str(r[14]));
            m.put("MAX_TON",        r[15]);
            m.put("HAS_DETAIL",     "Y");
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
    // KNRAWMS.BZPTN 원장 → wmsEm (WMS Oracle)
    // KNRAWMS.BZPTN_DETAIL  → bzptnDetailRepo / tmsEm (TMS Oracle)
    // ──────────────────────────────────────────────────────────────────────────
    public Map<String, Object> getDetail(String ptnrky, String ptnrty, String ownrky) {
        @SuppressWarnings("unchecked")
        List<Object[]> bRows = wmsEm.createNativeQuery(
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
    // ──────────────────────────────────────────────────────────────────────────
    public List<Object[]> searchRouteCost(String wareky, String ptnrky, String cartype) {
        return routeCostRepo.searchList(
            nullIfBlank(wareky), nullIfBlank(ptnrky), nullIfBlank(cartype)
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 운송비 피벗 (Flask api_route_cost_pivot)
    // ──────────────────────────────────────────────────────────────────────────
    public Map<String, Object> pivotRouteCost(String wareky, String ptnrky) {
        List<Object[]> rows = routeCostRepo.searchList(nullIfBlank(wareky), nullIfBlank(ptnrky), null);

        List<String> cartypes = new ArrayList<>();
        Map<String, Map<String, Double>> pivotMap = new LinkedHashMap<>();

        for (Object[] r : rows) {
            String pt = str(r[1]);
            String ct = str(r[2]);
            double cost = toDouble(r[3]);
            if (!cartypes.contains(ct)) cartypes.add(ct);
            pivotMap.computeIfAbsent(pt, x -> new LinkedHashMap<>()).put(ct, cost);
        }

        List<Map<String, Object>> pivotRows = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> e : pivotMap.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("PTNRKY", e.getKey());
            row.putAll(e.getValue());
            pivotRows.add(row);
        }
        return Map.of("cartypes", cartypes, "rows", pivotRows);
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
