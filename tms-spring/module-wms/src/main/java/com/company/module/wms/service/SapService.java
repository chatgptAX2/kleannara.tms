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
 * SAP RFC 연동 및 PS배차 확장 서비스
 *
 * ■ DataSource 라우팅
 *   wmsJdbc (Oracle KNRAWMS): SHPDH, SHPDI, BZPTN (납품예정 조회)
 *   tmsJdbc (MariaDB TMS):    PS_DISPATCH_H/D, VHCMA, DOC_FILE, PS_DISPATCH_SPLIT
 */
@Slf4j
@Service
public class SapService {

    /** Oracle WMS — KNRAWMS.SHPDH / SHPDI / BZPTN 조회 */
    private final JdbcTemplate        wmsJdbc;
    /** MariaDB TMS — PS_DISPATCH_H/D, VHCMA 등 직접 조작 */
    private final JdbcTemplate        tmsJdbc;
    private final SapRfcService        sapRfc;
    private final AutoDispatchService  autoDispatch;

    public SapService(
            @Qualifier("wmsJdbcTemplate") JdbcTemplate wmsJdbc,
            @Qualifier("tmsJdbcTemplate") JdbcTemplate tmsJdbc,
            SapRfcService sapRfc,
            AutoDispatchService autoDispatch) {
        this.wmsJdbc      = wmsJdbc;
        this.tmsJdbc      = tmsJdbc;
        this.sapRfc       = sapRfc;
        this.autoDispatch = autoDispatch;
    }

    private static final DateTimeFormatter YMDFORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HMSFORMAT = DateTimeFormatter.ofPattern("HHmmss");

    // ── 납품처 관리 메뉴 — 출하지점(창고) 목록 조회 ─────────────────────────
    // 프론트엔드 dlvState.codes['TMS_SHPPOINT'] 로 저장되어 창고 select 옵션에 사용
    // KNRAWMS.BZPTN (납품처 마스터, PTNRTY='WH') → {value: PTNRKY, label: NAME01} 목록 반환
    // wareky/dateFrom/dateTo 파라미터는 레거시 시그니처 유지 (사용하지 않음)
    public Map<String, Object> shppoint(String wareky, String dateFrom, String dateTo) {
        try {
            List<Map<String, Object>> rows = wmsJdbc.queryForList(
                "SELECT PTNRKY AS value, NAME01 AS label" +
                " FROM KNRAWMS.BZPTN" +
                " WHERE PTNRTY = 'WH'" +
                " ORDER BY PTNRKY"
            );
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) { return errMap(e); }
    }

    // ── SAP 선적 생성 — SapRfcService (Z_TMS_SHIPMENT_CRDL RFC) 로 위임 ──
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

    // ── 배차 삭제(가배차 이력 삭제) — SapRfcService 로 위임 ─────────
    public Map<String, Object> sapDelete(Map<String, Object> body) {
        try {
            return sapRfc.sapDelete(body);
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
            // ■ 동적 WHERE (소프트파싱용 '? IS NULL OR ...' 고정조건 제거)
            //   값이 존재하는 조건만 WHERE 절 + 바인드 파라미터로 추가한다.
            String p1 = (dateFrom != null && !dateFrom.isBlank()) ? dateFrom.replace("-", "") : null;
            String p3 = (dateTo   != null && !dateTo.isBlank())   ? dateTo.replace("-", "")   : null;
            String p5 = (dptnky   != null && !dptnky.isBlank())   ? dptnky                    : null;
            String p7 = (shpoky   != null && !shpoky.isBlank())   ? shpoky                    : null;

            StringBuilder where = new StringBuilder();
            List<Object> params = new ArrayList<>();
            if (p1 != null) { where.append(where.length() == 0 ? " WHERE" : " AND").append(" h.RQSHPD>=?"); params.add(p1); }
            if (p3 != null) { where.append(where.length() == 0 ? " WHERE" : " AND").append(" h.RQSHPD<=?"); params.add(p3); }
            if (p5 != null) { where.append(where.length() == 0 ? " WHERE" : " AND").append(" h.DPTNKY=?");  params.add(p5); }
            if (p7 != null) { where.append(where.length() == 0 ? " WHERE" : " AND").append(" h.SHPOKY=?");  params.add(p7); }

            final String sql =
                "SELECT h.SHPOKY, h.DPTNKY, COALESCE(b.NAME01,h.DPTNKY) AS DPTNM, " +
                "       h.RQSHPD, COUNT(i.SHPOIT) AS ITEM_CNT, SUM(i.QTSHPO) AS TOTAL_QTY " +
                "FROM KNRAWMS.SHPDH h JOIN KNRAWMS.SHPDI i ON h.SHPOKY=i.SHPOKY " +
                "LEFT JOIN KNRAWMS.BZPTN b ON b.PTNRKY=h.DPTNKY AND b.PTNRTY='CT'" +
                where +
                " GROUP BY h.SHPOKY, h.DPTNKY, b.NAME01, h.RQSHPD ORDER BY h.RQSHPD, h.DPTNKY";
            List<Map<String, Object>> rows = wmsJdbc.queryForList(sql, params.toArray());
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) { return errMap(e); }
    }

    /** 자동배차 — AutoDispatchService 로 위임 */
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
            // PS_DISPATCH_H / PS_DISPATCH_D → MariaDB tmsJdbc
            List<Map<String, Object>> heads = tmsJdbc.queryForList(
                "SELECT * FROM KNRAWMS.PS_DISPATCH_H WHERE DISP_H_ID=?", dispHId
            );
            List<Map<String, Object>> details = tmsJdbc.queryForList(
                "SELECT * FROM KNRAWMS.PS_DISPATCH_D WHERE DISP_H_ID=? ORDER BY ITEM_SEQ", dispHId
            );
            return Map.of("ok", true, "header", heads.isEmpty() ? null : heads.get(0), "details", details);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional
    public Map<String, Object> psDelete(Map<String, Object> body) {
        Long dispHId = toLong(body.get("disp_h_id"));
        if (dispHId == null) return Map.of("ok", false, "error", "disp_h_id 필수");
        try {
            // PS_DISPATCH_D / PS_DISPATCH_H → MariaDB tmsJdbc
            tmsJdbc.update("DELETE FROM KNRAWMS.PS_DISPATCH_D WHERE DISP_H_ID=?", dispHId);
            tmsJdbc.update("DELETE FROM KNRAWMS.PS_DISPATCH_H WHERE DISP_H_ID=?", dispHId);
            return Map.of("ok", true);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional
    /**
     * 납품분할 저장 — 납품문서(SVBELN) 단위 요청.
     *
     * 요청 형식(프론트 psdExecuteSplit):
     *   {
     *     "SVBELN": "0823932282",
     *     "splits": [
     *       {"SVBELN":"0823932282","SPOSNR":"000010","skukey":"...","desc01":"...","org_qty":498,"split_qty":98},
     *       {"SVBELN":"0823932282","SPOSNR":"000020","skukey":"...","desc01":"...","org_qty":677,"split_qty":77}
     *     ]
     *   }
     *
     * 처리 흐름:
     *   1) splits 검증
     *   2) SAP RFC 호출 (납품문서 단위 1회) — SapRfcService.shipmentSplit 로 위임
     *      ※ RFC 개발 진행중 → 실제 호출은 SapRfcService 내부에서 if(false) 로 비활성화됨.
     *   3) RFC 성공 시 WMS API(WMS_IFC301) 호출 (SapRfcService 내부에서 수행)
     */
    public Map<String, Object> psSplit(Map<String, Object> body) {
        try {
            String svbeln = str(body.get("SVBELN"));
            if (svbeln.isBlank()) svbeln = str(body.get("svbeln"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> splits =
                (List<Map<String, Object>>) (body.get("splits") != null ? body.get("splits") : body.get("split_items"));
            if (splits == null || splits.isEmpty())
                return Map.of("ok", false, "error", "splits 필수");
            if (svbeln.isBlank()) {
                // SVBELN 미전달 시 splits 첫 항목에서 유추
                svbeln = str(splits.get(0).get("SVBELN"));
            }
            if (svbeln.isBlank())
                return Map.of("ok", false, "error", "납품문서(SVBELN) 필수");

            // ── SAP RFC(납품문서 단위) + RFC 성공 시 WMS API 호출을 SapRfcService 로 위임 ──
            //    RFC 개발 완료 전까지 실제 RFC 호출은 SapRfcService 내부 if(false) 로 비활성화.
            return sapRfc.shipmentSplit(svbeln, splits);
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
            // PS_DISPATCH_D → MariaDB tmsJdbc
            tmsJdbc.update("UPDATE KNRAWMS.PS_DISPATCH_D SET " + String.join(",", sets) + " WHERE DISP_D_ID=?", args.toArray());
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

            // PS_DISPATCH_H → MariaDB tmsJdbc
            String dispatchNo = "PS" + dispDate + now;
            tmsJdbc.update(
                "INSERT INTO KNRAWMS.PS_DISPATCH_H (DISPATCH_NO,DPTNKY,DPTNM,DISP_DATE,STATUS,CARTYPE,NOTE,CREDAT,CRETIM,LMODAT) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?)",
                dispatchNo, dptnky, dptnm, dispDate, "DRAFT", cartype,
                str(body.get("note")), today, now, today
            );
            Long newId = tmsJdbc.queryForObject("SELECT SEQ_PS_DISPATCH_H.CURRVAL FROM DUAL", Long.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
            if (items != null) {
                int seq = 1;
                for (Map<String, Object> it : items) {
                    // PS_DISPATCH_D → MariaDB tmsJdbc
                    tmsJdbc.update(
                        "INSERT INTO KNRAWMS.PS_DISPATCH_D (DISP_H_ID,SHPOKY,SHPOIT,SKUKEY,QTSHPO,KG_WEIGHT,ITEM_SEQ,CREDAT) VALUES (?,?,?,?,?,?,?,?)",
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
