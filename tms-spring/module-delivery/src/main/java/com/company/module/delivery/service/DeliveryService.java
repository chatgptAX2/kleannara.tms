package com.company.module.delivery.service;

import com.company.module.delivery.dto.*;
import com.company.module.delivery.entity.BzptnDetail;
import com.company.module.delivery.entity.RouteCost;
import com.company.module.delivery.repository.BzptnDetailRepository;
import com.company.module.delivery.repository.RouteCostRepository;
import org.springframework.beans.factory.annotation.Qualifier;
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
 *   tmsEm  (tmsPU / MariaDB) :
 *     - BzptnDetailRepository (BZPTN_DETAIL)  -- TMS 추가 상세 테이블
 *     - RouteCostRepository   (ROUTE_COST)    -- 운송경로비용
 *     - em.createNativeQuery  UPDATE/INSERT bzptn_detail
 *
 *   wmsEm  (wmsPU / Oracle WMS) :
 *     - em.createNativeQuery  SELECT * FROM BZPTN  -- WMS 원장 테이블
 * ───────────────────────────────────────────────────────────────
 */
@Service
@Transactional(readOnly = true, transactionManager = "tmsTransactionManager")
public class DeliveryService {

    private final BzptnDetailRepository bzptnDetailRepo;
    private final RouteCostRepository   routeCostRepo;

    /** TMS DB (MariaDB) — BZPTN_DETAIL / ROUTE_COST 쓰기용 */
    @PersistenceContext(unitName = "tmsPU")
    private EntityManager tmsEm;

    /** WMS DB (Oracle) — BZPTN 원장 읽기용 */
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
    // ──────────────────────────────────────────────────────────────────────────
    public Map<String, Object> getList(DeliverySearchRequest req) {
        int page   = req.getPage() == null ? 1 : req.getPage();
        int size   = req.getSize() == null ? 50 : req.getSize();
        int offset = (page - 1) * size;

        String wareky    = nullIfBlank(req.getVstel());
        String itemGroup = nullIfBlank(req.getSkug05());
        String ptnrky    = nullIfBlank(req.getPtnrky());
        String q         = nullIfBlank(req.getQ());

        long total = bzptnDetailRepo.searchCount(wareky, itemGroup, ptnrky, q);
        List<Object[]> rows = bzptnDetailRepo.searchList(wareky, itemGroup, ptnrky, q, size, offset);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("PTNRKY",      str(r[0]));  m.put("NAME01",    str(r[1]));
            m.put("PTNRTY",      str(r[2]));  m.put("OWNRKY",    str(r[3]));
            m.put("ADDR01",      str(r[4]));  m.put("ADDR02",    str(r[5]));
            m.put("REGN01",      str(r[6]));  m.put("TELN01",    str(r[7]));
            m.put("WAREKY",      str(r[8]));  m.put("ROUTE_CD",  str(r[9]));
            m.put("ITEM_GROUP",  str(r[10])); m.put("AREA_CD",   str(r[11]));
            m.put("UNLOAD_TIME", r[12]);      m.put("MAX_HEIGHT",r[13]);
            m.put("AUTO_ALLOC_YN", str(r[14])); m.put("FORKLIFT_YN", str(r[15]));
            m.put("INB_TIME_FROM1", str(r[16])); m.put("INB_TIME_TO1", str(r[17]));
            m.put("MAX_BOX_QTY", r[18]); m.put("DEADLINE_TIME", str(r[19]));
            m.put("MAX_TON", r[20]); m.put("HAS_DETAIL", str(r[21]));
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
    // BZPTN 원장 → wmsEm (Oracle), BZPTN_DETAIL 추가정보 → bzptnDetailRepo (MariaDB)
    // ──────────────────────────────────────────────────────────────────────────
    public Map<String, Object> getDetail(String ptnrky, String ptnrty, String ownrky) {
        @SuppressWarnings("unchecked")
        List<Object[]> bRows = wmsEm.createNativeQuery(
            "SELECT * FROM BZPTN WHERE PTNRKY=? AND PTNRTY=? AND OWNRKY=?")
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
    // BZPTN_DETAIL 쓰기 → tmsEm (MariaDB)
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
                UPDATE bzptn_detail SET
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
                INSERT INTO bzptn_detail
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
    // 납품처 삭제 (Flask api_delivery_delete) — TMS DB
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

        // CARTYPE 고유 목록 수집 (컬럼)
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
        // Object[] from BZPTN native query → 단순 Map 반환
        if (o == null) return Map.of();
        return Map.of("raw", o.toString()); // 실제 배포 시 column 매핑 필요
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
