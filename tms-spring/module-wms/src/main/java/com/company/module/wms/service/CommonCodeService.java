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
    public Map<String, Object> getCodes(String cmcdky) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT CMCDVL, CDESC1, CDESC2, USARG1, USARG2, USARG3, USARG4, USARG5, SORTNO " +
                "FROM KNRAWMS.CMCDV WHERE CMCDKY = ? ORDER BY SORTNO, CMCDVL", cmcdky
            );
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
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
            if (exists.isEmpty()) {
                jdbc.update(
                    "INSERT INTO WAHMA (WAREKY, WARENM, WADDR1, WADDR2, POSTCD, TELNO, FAXNO, USARG1, USARG2, ACTIVE, CREDAT, LMODAT) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    wareky, warenm,
                    body.get("WADDR1"), body.get("WADDR2"), body.get("POSTCD"),
                    body.get("TELNO"), body.get("FAXNO"),
                    body.get("USARG1"), body.get("USARG2"),
                    body.getOrDefault("ACTIVE", "Y"),
                    today, today
                );
            } else {
                jdbc.update(
                    "UPDATE WAHMA SET WARENM=?, WADDR1=?, WADDR2=?, POSTCD=?, TELNO=?, FAXNO=?, " +
                    "USARG1=?, USARG2=?, ACTIVE=?, LMODAT=? WHERE WAREKY=?",
                    warenm,
                    body.get("WADDR1"), body.get("WADDR2"), body.get("POSTCD"),
                    body.get("TELNO"), body.get("FAXNO"),
                    body.get("USARG1"), body.get("USARG2"),
                    body.getOrDefault("ACTIVE", "Y"),
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
