package com.company.module.wms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * SAP RFC 연동 및 PS배차 확장 서비스 (WMS Oracle DB)
 */
@Slf4j
@Service
public class SapService {

    private final JdbcTemplate        jdbc;
    private final SapRfcService        sapRfc;
    private final AutoDispatchService  autoDispatch;

    public SapService(
            @Qualifier("wmsJdbcTemplate") JdbcTemplate jdbc,
            SapRfcService sapRfc,
            AutoDispatchService autoDispatch) {
        this.jdbc         = jdbc;
        this.sapRfc       = sapRfc;
        this.autoDispatch = autoDispatch;
    }

    private static final DateTimeFormatter YMDFORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HMSFORMAT = DateTimeFormatter.ofPattern("HHmmss");

    // ── 납품처 출고예정 포인트 조회 (SHPDH 기반) ─────────────────
    public Map<String, Object> shppoint(String wareky, String dateFrom, String dateTo) {
        try {
            StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT h.DPTNKY AS PTNRKY, COALESCE(b.NAME01,h.DPTNKY) AS PTNRNM, " +
                "       h.RQSHPD, COUNT(*) AS DOC_CNT " +
                "FROM SHPDH h LEFT JOIN BZPTN b ON b.PTNRKY=h.DPTNKY AND b.PTNRTY='CT' " +
                "WHERE 1=1"
            );
            List<Object> args = new ArrayList<>();
            if (wareky != null && !wareky.isBlank()) { sql.append(" AND h.WAREKY=?"); args.add(wareky); }
            if (dateFrom != null && !dateFrom.isBlank()) { sql.append(" AND h.RQSHPD>=?"); args.add(dateFrom.replace("-", "")); }
            if (dateTo   != null && !dateTo.isBlank())   { sql.append(" AND h.RQSHPD<=?"); args.add(dateTo.replace("-", "")); }
            sql.append(" GROUP BY h.DPTNKY, b.NAME01, h.RQSHPD ORDER BY h.RQSHPD, h.DPTNKY");
            List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) { return errMap(e); }
    }

    // ── SAP 선적 생성 — SapRfcService (Z_TMS_SHIPMENT_CRDL RFC + WMS IFC301) 로 위임 ──
    public Map<String, Object> shipmentCreate(Map<String, Object> body) {
        try {
            return sapRfc.shipmentCreate(body);
        } catch (Exception e) { return errMap(e); }
    }

    // ── SAP 선적 삭제 — SapRfcService 로 위임 ──────────────────────
    public Map<String, Object> shipmentDelete(Map<String, Object> body) {
        try {
            return sapRfc.shipmentDelete(body);
        } catch (Exception e) { return errMap(e); }
    }

    // ── SAP 차량 검색 — SapRfcService 로 위임 ─────────────────────
    public Map<String, Object> vehicleSearch(Map<String, Object> body) {
        try { return sapRfc.vehicleSearch(body); } catch (Exception e) { return errMap(e); }
    }

    // ── SAP 차량 배정 — SapRfcService 로 위임 ─────────────────────
    public Map<String, Object> assignVehicle(Map<String, Object> body) {
        try { return sapRfc.assignVehicle(body); } catch (Exception e) { return errMap(e); }
    }

    // ── SAP 선적 목록 — SapRfcService 로 위임 ─────────────────────
    public Map<String, Object> sapList(Map<String, Object> body) {
        try { return sapRfc.sapList(body); } catch (Exception e) { return errMap(e); }
    }

    // ── SAP 선적 아이템 — SapRfcService 로 위임 ───────────────────
    public Map<String, Object> sapItems(Map<String, Object> body) {
        try { return sapRfc.sapItems(body); } catch (Exception e) { return errMap(e); }
    }

    // ── SAP 서류 목록 — SapRfcService 로 위임 ─────────────────────
    public Map<String, Object> sapDocs(Map<String, Object> body) {
        try { return sapRfc.sapDocs(body); } catch (Exception e) { return errMap(e); }
    }

    // ── ps-dispatch 추가 API ──────────────────────────────────────

    public Map<String, Object> psSearch(String dateFrom, String dateTo, String dptnky, String shpoky, String status) {
        try {
            StringBuilder sql = new StringBuilder(
                "SELECT h.SHPOKY, h.DPTNKY, COALESCE(b.NAME01,h.DPTNKY) AS DPTNM, " +
                "       h.RQSHPD, COUNT(i.SHPOIT) AS ITEM_CNT, SUM(i.QTSHPO) AS TOTAL_QTY " +
                "FROM SHPDH h JOIN SHPDI i ON h.SHPOKY=i.SHPOKY " +
                "LEFT JOIN BZPTN b ON b.PTNRKY=h.DPTNKY AND b.PTNRTY='CT' " +
                "WHERE 1=1"
            );
            List<Object> args = new ArrayList<>();
            if (dateFrom != null && !dateFrom.isBlank()) { sql.append(" AND h.RQSHPD>=?"); args.add(dateFrom.replace("-","")); }
            if (dateTo   != null && !dateTo.isBlank())   { sql.append(" AND h.RQSHPD<=?"); args.add(dateTo.replace("-","")); }
            if (dptnky   != null && !dptnky.isBlank())   { sql.append(" AND h.DPTNKY=?"); args.add(dptnky); }
            if (shpoky   != null && !shpoky.isBlank())   { sql.append(" AND h.SHPOKY=?"); args.add(shpoky); }
            sql.append(" GROUP BY h.SHPOKY, h.DPTNKY, b.NAME01, h.RQSHPD ORDER BY h.RQSHPD, h.DPTNKY");
            List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) { return errMap(e); }
    }

    /** 자동배차 — AutoDispatchService (FFD/BFD/MIN_COST 완전 구현) 로 위임 */
    public Map<String, Object> psAuto(Map<String, Object> body) {
        try {
            return autoDispatch.runAuto(body);
        } catch (Exception e) {
            return errMap(e);
        }
    }

    public Map<String, Object> psLoadForEdit(Map<String, Object> body) {
        Long dispHId = toLong(body.get("disp_h_id"));
        if (dispHId == null) return Map.of("ok", false, "error", "disp_h_id 필수");
        try {
            List<Map<String, Object>> heads = jdbc.queryForList(
                "SELECT * FROM PS_DISPATCH_H WHERE DISP_H_ID=?", dispHId
            );
            List<Map<String, Object>> details = jdbc.queryForList(
                "SELECT * FROM PS_DISPATCH_D WHERE DISP_H_ID=? ORDER BY ITEM_SEQ", dispHId
            );
            return Map.of("ok", true, "header", heads.isEmpty() ? null : heads.get(0), "details", details);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional
    public Map<String, Object> psDelete(Map<String, Object> body) {
        Long dispHId = toLong(body.get("disp_h_id"));
        if (dispHId == null) return Map.of("ok", false, "error", "disp_h_id 필수");
        try {
            jdbc.update("DELETE FROM PS_DISPATCH_D WHERE DISP_H_ID=?", dispHId);
            jdbc.update("DELETE FROM PS_DISPATCH_H WHERE DISP_H_ID=?", dispHId);
            return Map.of("ok", true);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional
    public Map<String, Object> psSplit(Map<String, Object> body) {
        Long dispHId = toLong(body.get("disp_h_id"));
        if (dispHId == null) return Map.of("ok", false, "error", "disp_h_id 필수");
        String today = LocalDate.now().format(YMDFORMAT);
        String now   = LocalDateTime.now().format(HMSFORMAT);
        try {
            // 분할 정보 저장
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> splitItems = (List<Map<String, Object>>) body.get("split_items");
            if (splitItems == null) return Map.of("ok", false, "error", "split_items 필수");
            int saved = 0;
            for (Map<String, Object> it : splitItems) {
                jdbc.update(
                    "INSERT INTO PS_DISPATCH_SPLIT (DISP_H_ID,ORIG_ITEM,SPLIT_SEQ,SKUKEY,QTSHPO,KG_WEIGHT,NOTE,CREDAT,CRETIM) VALUES (?,?,?,?,?,?,?,?,?)",
                    dispHId, it.get("orig_item"), it.get("split_seq"), it.get("skukey"),
                    it.get("qtshpo"), it.get("kg_weight"), it.get("note"), today, now
                );
                saved++;
            }
            return Map.of("ok", true, "saved", saved);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional
    public Map<String, Object> psUpdateItem(Map<String, Object> body) {
        Long dispDId = toLong(body.get("disp_d_id"));
        if (dispDId == null) return Map.of("ok", false, "error", "disp_d_id 필수");
        String today = LocalDate.now().format(YMDFORMAT);
        try {
            List<String> sets = new ArrayList<>();
            List<Object> args = new ArrayList<>();
            Map<String, String> updatableFields = Map.of(
                "CARTYPE","CARTYPE","QTSHPO","QTSHPO","KG_WEIGHT","KG_WEIGHT",
                "NOTE","NOTE","ITEM_SEQ","ITEM_SEQ"
            );
            for (Map.Entry<String, String> e : updatableFields.entrySet()) {
                if (body.containsKey(e.getKey())) { sets.add(e.getValue()+"=?"); args.add(body.get(e.getKey())); }
            }
            if (sets.isEmpty()) return Map.of("ok", false, "error", "변경 필드 없음");
            sets.add("LMODAT=?"); args.add(today); args.add(dispDId);
            jdbc.update("UPDATE PS_DISPATCH_D SET " + String.join(",", sets) + " WHERE DISP_D_ID=?", args.toArray());
            return Map.of("ok", true);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional
    public Map<String, Object> psCreateManual(Map<String, Object> body) {
        String today = LocalDate.now().format(YMDFORMAT);
        String now   = LocalDateTime.now().format(HMSFORMAT);
        try {
            String dptnky  = str(body.get("dptnky"));
            String dptnm   = str(body.get("dptnm"));
            String dispDate= str(body.getOrDefault("disp_date", today));
            String cartype = str(body.get("cartype"));
            if (dptnky.isBlank()) return Map.of("ok", false, "error", "dptnky 필수");

            // 배차번호 생성
            String dispatchNo = "PS" + dispDate + now;
            jdbc.update(
                "INSERT INTO PS_DISPATCH_H (DISPATCH_NO,DPTNKY,DPTNM,DISP_DATE,STATUS,CARTYPE,NOTE,CREDAT,CRETIM,LMODAT) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?)",
                dispatchNo, dptnky, dptnm, dispDate, "DRAFT", cartype,
                str(body.get("note")), today, now, today
            );
            Long newId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
            if (items != null) {
                int seq = 1;
                for (Map<String, Object> it : items) {
                    jdbc.update(
                        "INSERT INTO PS_DISPATCH_D (DISP_H_ID,SHPOKY,SHPOIT,SKUKEY,QTSHPO,KG_WEIGHT,ITEM_SEQ,CREDAT) VALUES (?,?,?,?,?,?,?,?)",
                        newId, it.get("shpoky"), it.get("shpoit"), it.get("skukey"),
                        it.get("qtshpo"), it.get("kg_weight"), seq++, today
                    );
                }
            }
            return Map.of("ok", true, "disp_h_id", newId, "dispatch_no", dispatchNo);
        } catch (Exception e) { return errMap(e); }
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────
    private Map<String, Object> errMap(Exception e) {
        log.error("SapService error: {}", e.getMessage());
        return Map.of("ok", false, "error", e.getMessage());
    }
    private String str(Object v) { return v == null ? "" : v.toString().trim(); }
    private Long toLong(Object v) {
        try { return v == null ? null : Long.valueOf(v.toString()); } catch (Exception e) { return null; }
    }
}
