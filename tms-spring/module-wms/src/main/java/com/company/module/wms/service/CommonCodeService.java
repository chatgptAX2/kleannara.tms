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
 * 공통코드 + 물류센터 + 출고문서 서비스 (WMS Oracle DB)
 */
@Slf4j
@Service
public class CommonCodeService {

    private final JdbcTemplate jdbc;

    public CommonCodeService(@Qualifier("wmsJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
    private static final DateTimeFormatter YMDFORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ── 공통코드 조회 ──────────────────────────────────────────────
    /**
     * 프론트엔드가 기대하는 배열 형식으로 반환.
     * <pre>
     *   CMCDVL  → value
     *   CDESC1  → label
     *   CDESC2  → desc2
     *   USARG1  → arg1
     *   USARG2  → arg2
     *   USARG3  → arg3
     *   USARG4  → arg4
     *   USARG5  → arg5
     *   SORTNO  → sortno (Oracle CMCDV에 미존재 — CMCDVL 정렬로 대체)
     * </pre>
     */
    public List<Map<String, Object>> getCodes(String cmcdky) {
        try {
            // Oracle KNRAWMS.CMCDV: SORTNO 컬럼 미존재 → SELECT/ORDER BY 제외
            // 운송사(TMS_CARRIER)는 사용여부(USARG1='Y') 코드만 조회.
            String sql = "SELECT CMCDVL, CDESC1, CDESC2, USARG1, USARG2, USARG3, USARG4, USARG5 " +
                         "FROM KNRAWMS.CMCDV WHERE CMCDKY = ?";
            if ("TMS_CARRIER".equalsIgnoreCase(cmcdky)) {
                sql += " AND USARG1 = 'Y'";
            }
            sql += " ORDER BY CMCDVL";
            List<Map<String, Object>> rows = jdbc.queryForList(sql, cmcdky);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("value",  r.get("CMCDVL"));
                item.put("label",  r.get("CDESC1"));
                item.put("desc2",  r.get("CDESC2"));
                item.put("arg1",   r.get("USARG1"));
                item.put("arg2",   r.get("USARG2"));
                item.put("arg3",   r.get("USARG3"));
                item.put("arg4",   r.get("USARG4"));
                item.put("arg5",   r.get("USARG5"));
                item.put("sortno", null); // Oracle CMCDV SORTNO 컬럼 없음 — null 반환
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            log.error("getCodes error [{}]: {}", cmcdky, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── 물류센터 목록 ──────────────────────────────────────────────
    public Map<String, Object> wahmaList() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM KNRAWMS.WAHMA ORDER BY WAREKY"
            );
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 물류센터 상세 ──────────────────────────────────────────────
    public Map<String, Object> wahmaDetail(String wareky) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM KNRAWMS.WAHMA WHERE WAREKY = ?", wareky
            );
            return Map.of("ok", true, "row", rows.isEmpty() ? null : rows.get(0));
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 물류센터 저장 (INSERT/UPDATE) ─────────────────────────────
    @Transactional
    public Map<String, Object> wahmaSave(Map<String, Object> body) {
        String today   = LocalDate.now().format(YMDFORMAT);
        String wareky  = (String) body.get("WAREKY");
        String warenm  = (String) body.get("WARENM");
        if (wareky == null || wareky.isBlank())
            return Map.of("ok", false, "error", "WAREKY 필수");

        try {
            List<Map<String, Object>> exists = jdbc.queryForList(
                "SELECT WAREKY FROM KNRAWMS.WAHMA WHERE WAREKY = ?", wareky
            );
            // ※ 실제 WAHMA 컬럼: 창고명 = NAME01 (WARENM 아님). ACTIVE 컬럼 미존재 → 제외.
            if (exists.isEmpty()) {
                jdbc.update(
                    "INSERT INTO KNRAWMS.WAHMA (WAREKY, NAME01, WADDR1, WADDR2, POSTCD, TELNO, FAXNO, USARG1, USARG2, CREDAT, LMODAT) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    wareky, warenm,
                    body.get("WADDR1"), body.get("WADDR2"), body.get("POSTCD"),
                    body.get("TELNO"), body.get("FAXNO"),
                    body.get("USARG1"), body.get("USARG2"),
                    today, today
                );
            } else {
                jdbc.update(
                    "UPDATE KNRAWMS.WAHMA SET NAME01=?, WADDR1=?, WADDR2=?, POSTCD=?, TELNO=?, FAXNO=?, " +
                    "USARG1=?, USARG2=?, LMODAT=? WHERE WAREKY=?",
                    warenm,
                    body.get("WADDR1"), body.get("WADDR2"), body.get("POSTCD"),
                    body.get("TELNO"), body.get("FAXNO"),
                    body.get("USARG1"), body.get("USARG2"),
                    today, wareky
                );
            }
            return Map.of("ok", true, "WAREKY", wareky);
        } catch (Exception e) {
            log.error("wahmaSave error: {}", e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 물류센터 삭제 ──────────────────────────────────────────────
    @Transactional
    public Map<String, Object> wahmaDelete(String wareky) {
        if (wareky == null || wareky.isBlank())
            return Map.of("ok", false, "error", "WAREKY 필수");
        try {
            int affected = jdbc.update("DELETE FROM KNRAWMS.WAHMA WHERE WAREKY = ?", wareky);
            return Map.of("ok", true, "affected", affected);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 출고문서 헤더 + 상세 조회 ─────────────────────────────────
    public Map<String, Object> shpdhDetail(String shpoky) {
        try {
            List<Map<String, Object>> header = jdbc.queryForList(
                "SELECT h.*, b.NAME01 AS DPTNM_FULL " +
                "FROM KNRAWMS.SHPDH h LEFT JOIN KNRAWMS.BZPTN b ON b.PTNRKY=h.DPTNKY AND b.PTNRTY='CT' " +
                "WHERE h.SHPOKY = ?", shpoky
            );
            if (header.isEmpty()) return Map.of("ok", false, "error", "문서 없음");

            List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT i.*, s.SKUNM AS SKUNM_FULL " +
                "FROM KNRAWMS.SHPDI i LEFT JOIN KNRAWMS.SKUMA s ON s.SKUKEY = i.SKUKEY " +
                "WHERE i.SHPOKY = ? ORDER BY i.SHPOIT", shpoky
            );
            return Map.of("ok", true, "header", header.get(0), "items", items);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }
}
