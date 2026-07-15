package com.company.app.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 애플리케이션 기동 시 TMS(Oracle KNRAWMS) / WMS(Oracle KNRAWMS) DataSource 접속 상태를 로그로 출력.
 *
 * ■ 출력 시점: Spring 컨텍스트 초기화 완료 직후 (@PostConstruct)
 *
 * ■ 출력 항목 (접속 성공 시)
 *   - DB 제품명 / 버전
 *   - JDBC URL
 *   - 접속 계정
 *   - 현재 스키마 (Oracle = 계정명)
 *   - DB 서버 현재 시각 (SYSDATE)
 *   - HikariPool 이름
 *
 * ■ 출력 항목 (접속 실패 시)
 *   - 실패 원인 메시지
 *   - 기동은 중단하지 않음 (warn 로그 후 계속)
 */
@Slf4j
@Configuration
public class DataSourceConnectionVerifier {

    private final DataSource tmsDataSource;
    private final DataSource wmsDataSource;

    public DataSourceConnectionVerifier(
            @Qualifier("tmsDataSource") DataSource tmsDataSource,
            @Qualifier("wmsDataSource") DataSource wmsDataSource) {
        this.tmsDataSource = tmsDataSource;
        this.wmsDataSource = wmsDataSource;
    }

    @PostConstruct
    public void verifyConnections() {
        log.info("┌─────────────────────────────────────────────────────────┐");
        log.info("│         DataSource 접속 상태 확인 (기동 시 1회)         │");
        log.info("└─────────────────────────────────────────────────────────┘");
        verifyTms();
        verifyWms();
        log.info("─────────────────────────────────────────────────────────");
    }

    // ── TMS DB (Oracle 19C KNRAWMS) ─────────────────────────────────────────────────────
    private void verifyTms() {
        log.info("[TMS-DB] 접속 확인 시작 — Oracle 19C KNRAWMS (Primary)");
        try (Connection conn = tmsDataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String currentTime    = queryScalar(conn, "SELECT TO_CHAR(SYSDATE,'YYYY-MM-DD HH24:MI:SS') FROM DUAL");
            String schema         = conn.getSchema();

            log.info("[TMS-DB] ✅ 접속 성공");
            log.info("[TMS-DB]   DB 제품  : {} {}", meta.getDatabaseProductName(), meta.getDatabaseProductVersion());
            log.info("[TMS-DB]   JDBC URL : {}", sanitizeUrl(meta.getURL()));
            log.info("[TMS-DB]   계정     : {}", meta.getUserName());
            log.info("[TMS-DB]   스키마   : {}", schema);
            log.info("[TMS-DB]   서버시각 : {}", currentTime);
            log.info("[TMS-DB]   Pool     : {}", getPoolName(tmsDataSource));

            // Oracle KNRAWMS 핵심 테이블 접근 확인
            verifyTmsTableAccess(conn);

        } catch (Exception e) {
            log.warn("[TMS-DB] ❌ 접속 실패 — {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    // ── WMS DB (Oracle) ──────────────────────────────────────────────────────
    private void verifyWms() {
        log.info("[WMS-DB] 접속 확인 시작 — Oracle KNMESWMS");
        try (Connection conn = wmsDataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String currentTime    = queryScalar(conn, "SELECT TO_CHAR(SYSDATE,'YYYY-MM-DD HH24:MI:SS') FROM DUAL");
            String schema         = conn.getSchema();

            log.info("[WMS-DB] ✅ 접속 성공");
            log.info("[WMS-DB]   DB 제품  : {} {}", meta.getDatabaseProductName(), meta.getDatabaseProductVersion());
            log.info("[WMS-DB]   JDBC URL : {}", sanitizeUrl(meta.getURL()));
            log.info("[WMS-DB]   계정     : {}", meta.getUserName());
            log.info("[WMS-DB]   스키마   : {}", schema);
            log.info("[WMS-DB]   서버시각 : {}", currentTime);
            log.info("[WMS-DB]   Pool     : {}", getPoolName(wmsDataSource));

            // 추가: KNRATMS 계정 접근 가능한 테이블 수 확인
            verifyWmsTableAccess(conn);

        } catch (Exception e) {
            log.warn("[WMS-DB] ❌ 접속 실패 — {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    // ── TMS 핵심 테이블 접근 가능 여부 확인 (Oracle KNRAWMS) ────────────
    private void verifyTmsTableAccess(Connection conn) {
        // Oracle KNRAWMS 계정 직소유 테이블 — 스키마 접두어 불필요
        String[] checkTables = {"PS_DISPATCH_H", "VHCMA", "DS_VEHICLE", "ROUTE_COST"};
        StringBuilder ok  = new StringBuilder();
        StringBuilder err = new StringBuilder();

        for (String tbl : checkTables) {
            try (var stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tbl)) {
                if (rs.next()) {
                    ok.append(tbl).append("(").append(rs.getLong(1)).append(") ");
                }
            } catch (SQLException e) {
                err.append(tbl).append(" ");
            }
        }
        if (ok.length() > 0)  log.info("[TMS-DB]   핵심테이블 ✅ : {}", ok.toString().trim());
        if (err.length() > 0) log.warn("[TMS-DB]   핵심테이블 ❌ : {} — 접근 불가 (권한 또는 테이블 미존재)", err.toString().trim());
    }

    // ── WMS 핵심 테이블 접근 가능 여부 확인 (Oracle KNRAWMS) ─────────────────
    private void verifyWmsTableAccess(Connection conn) {
        // Oracle KNRAWMS 스키마 테이블 — 스키마명 접두어 필수
        String[] checkTables = {"KNRAWMS.CMCDM", "KNRAWMS.BZPTN", "KNRAWMS.SHPDH", "KNRAWMS.VHCMA", "KNRAWMS.BZPTN_DETAIL"};
        StringBuilder ok  = new StringBuilder();
        StringBuilder err = new StringBuilder();

        for (String tbl : checkTables) {
            try (var stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tbl)) {
                if (rs.next()) {
                    ok.append(tbl).append("(").append(rs.getLong(1)).append(") ");
                }
            } catch (SQLException e) {
                err.append(tbl).append(" ");
            }
        }
        if (ok.length() > 0)  log.info("[WMS-DB]   핵심테이블 ✅ : {}", ok.toString().trim());
        if (err.length() > 0) log.warn("[WMS-DB]   핵심테이블 ❌ : {} — 접근 불가 (권한 또는 테이블 미존재)", err.toString().trim());
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    /** 단일 값 쿼리 */
    private String queryScalar(Connection conn, String sql) {
        try (var stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? String.valueOf(rs.getObject(1)) : "N/A";
        } catch (Exception e) {
            return "N/A (" + e.getMessage() + ")";
        }
    }

    /** HikariDataSource 에서 pool-name 추출 */
    private String getPoolName(DataSource ds) {
        try {
            // HikariDataSource.getPoolName() — 리플렉션 없이 직접 캐스팅
            if (ds instanceof com.zaxxer.hikari.HikariDataSource hikari) {
                return hikari.getPoolName();
            }
        } catch (Exception ignored) {}
        return ds.getClass().getSimpleName();
    }

    /**
     * JDBC URL에서 패스워드 파라미터 마스킹
     * (URL에 password= 파라미터가 포함된 경우 대비)
     */
    private String sanitizeUrl(String url) {
        if (url == null) return "N/A";
        return url.replaceAll("(?i)(password=)[^&;]*", "$1****");
    }
}
