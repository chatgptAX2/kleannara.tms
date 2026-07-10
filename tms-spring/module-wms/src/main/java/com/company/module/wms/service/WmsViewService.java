package com.company.module.wms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * WMS 뷰어 서비스 — JDBC 기반 동적 쿼리 (WMS Oracle DB)
 */
@Slf4j
@Service
public class WmsViewService {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource   dataSource;

    public WmsViewService(
            @Qualifier("wmsJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Qualifier("wmsDataSource")   DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource   = dataSource;
    }

    // ── 허용 테이블 목록 (Flask TABLE_META 기반) ────────────────────
    // 인증된 사용자만 접근하므로 목록을 화이트리스트로 관리
    /** Oracle WMS(KNRAWMS 스키마) 소속 테이블 — 조회 시 스키마명 접두어 필요 */
    private static final Set<String> ORACLE_WMS_TABLES = new HashSet<>(Arrays.asList(
        "CMCDM", "CMCDV", "WAHMA", "SKUMA", "BZPTN", "MEASI",
        "SHPDH", "SHPDI", "IFWMS113", "RECDI", "BZPTN_DETAIL"
    ));

    private static final Set<String> ALLOWED_TABLES = new LinkedHashSet<>(Arrays.asList(
        "CMCDM", "CMCDV", "WAHMA", "SKUMA", "BZPTN", "MEASI",
        "SHPDH", "SHPDI", "IFWMS113",
        "BZPTN_DETAIL", "VHCMA", "ROUTE_COST", "DS_VEHICLE",
        "PS_DISPATCH_H", "PS_DISPATCH_D", "PS_DISPATCH_SPLIT",
        "DS_INCH12", "DS_INCH3",
        "DS_DISPATCH_OBJECTIVE", "DS_DISPATCH_CONST_SET", "DS_DISPATCH_CONST_SET_ITEM",
        "DS_DISPATCH_PROFILE", "DS_DISPATCH_CONSTRAINT", "DS_DISPATCH_CONST",
        "DOC_FOLDER", "DOC_FILE", "RECDI"
    ));

    // 읽기 전용 테이블 (INSERT/UPDATE/DELETE 불가)
    private static final Set<String> READONLY_TABLES = new HashSet<>(Arrays.asList(
        "SHPDH", "SHPDI", "IFWMS113"
    ));

    // SELECT 전용 SQL 패턴 체크
    private static final Pattern SELECT_ONLY = Pattern.compile(
        "^\\s*(SELECT|WITH)\\s+.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // ── 테이블 목록 ────────────────────────────────────────────────
    public Map<String, Object> getTables() {
        List<Map<String, Object>> tables = new ArrayList<>();
        for (String tbl : ALLOWED_TABLES) {
            // ORACLE_WMS_TABLES 소속이면 KNRAWMS. 접두어 붙여서 쿼리
            String qualifiedTbl = ORACLE_WMS_TABLES.contains(tbl)
                ? "KNRAWMS." + tbl : tbl;
            try {
                Long cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + qualifiedTbl, Long.class
                );
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", tbl);          // UI에는 원시 테이블명 표시
                row.put("rows", cnt != null ? cnt : 0);
                tables.add(row);
            } catch (Exception e) {
                // 테이블 미존재 시 목록에서 제외
                log.debug("Table {} not found: {}", qualifiedTbl, e.getMessage());
            }
        }
        return Map.of("ok", true, "tables", tables);
    }

    // ── 테이블 스키마 (컬럼 목록) ─────────────────────────────────
    public Map<String, Object> getSchema(String table) {
        String upper = table.toUpperCase();
        if (!ALLOWED_TABLES.contains(upper))
            throw new IllegalArgumentException("허용되지 않는 테이블: " + table);

        // Oracle JDBC DatabaseMetaData 는 schema/tableName 을 분리해서 넘겨야 함.
        // "KNRAWMS.CMCDM" 을 그대로 tableName 으로 넘기면 컬럼을 못 찾음.
        boolean isOracleWms = ORACLE_WMS_TABLES.contains(upper);
        String schemaParam = isOracleWms ? "KNRAWMS" : null;
        String tableParam  = upper;   // 순수 테이블명 (점 없음)
        String qualifiedTbl = isOracleWms ? "KNRAWMS." + upper : upper;

        List<Map<String, Object>> cols = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            // PK 컬럼 조회 — schema 분리
            Set<String> pkCols = new HashSet<>();
            try (ResultSet pk = meta.getPrimaryKeys(null, schemaParam, tableParam)) {
                while (pk.next()) pkCols.add(pk.getString("COLUMN_NAME"));
            }
            // 컬럼 목록 — schema 분리
            try (ResultSet rs = meta.getColumns(null, schemaParam, tableParam, null)) {
                while (rs.next()) {
                    Map<String, Object> col = new LinkedHashMap<>();
                    String colNm = rs.getString("COLUMN_NAME");
                    col.put("name", colNm);
                    col.put("type", rs.getString("TYPE_NAME"));
                    col.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    col.put("pk", pkCols.contains(colNm));
                    col.put("default", rs.getString("COLUMN_DEF"));
                    col.put("size", rs.getInt("COLUMN_SIZE"));
                    col.put("remark", rs.getString("REMARKS"));
                    cols.add(col);
                }
            }
            // 컬럼 목록이 비어 있으면 — MariaDB 테이블은 schema 파라미터 무시하고 재시도
            if (cols.isEmpty() && !isOracleWms) {
                try (ResultSet rs = meta.getColumns(null, null, tableParam, null)) {
                    while (rs.next()) {
                        Map<String, Object> col = new LinkedHashMap<>();
                        String colNm = rs.getString("COLUMN_NAME");
                        col.put("name", colNm);
                        col.put("type", rs.getString("TYPE_NAME"));
                        col.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                        col.put("pk", pkCols.contains(colNm));
                        col.put("default", rs.getString("COLUMN_DEF"));
                        col.put("size", rs.getInt("COLUMN_SIZE"));
                        col.put("remark", rs.getString("REMARKS"));
                        cols.add(col);
                    }
                }
            }
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
        return Map.of("ok", true, "table", qualifiedTbl, "columns", cols);
    }

    // ── 테이블 데이터 조회 ─────────────────────────────────────────
    public Map<String, Object> getData(String table, int page, int size,
                                       String search, String sort, String order) {
        String tbl = validateTable(table);
        int offset = Math.max(0, (page - 1)) * size;
        String safeOrder = "ASC".equalsIgnoreCase(order) ? "ASC" : "DESC";
        String safeSort  = (sort != null && sort.matches("[A-Za-z0-9_]+")) ? sort : null;

        try {
            // 전체 건수
            String cntSql = "SELECT COUNT(*) FROM " + tbl;
            Long total = jdbcTemplate.queryForObject(cntSql, Long.class);

            // ── Oracle 페이징: FETCH FIRST ... ROWS ONLY (Oracle 12c+ 표준)
            //    MySQL/MariaDB 의 LIMIT ? OFFSET ? 은 Oracle 에서 지원하지 않음.
            //    Oracle 표준 문법: ... ORDER BY col OFFSET n ROWS FETCH NEXT m ROWS ONLY
            StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tbl);
            if (safeSort != null) {
                sql.append(" ORDER BY ").append(safeSort).append(" ").append(safeOrder);
            } else {
                // Oracle 에서 OFFSET/FETCH 는 ORDER BY 가 없으면 결과 순서 불안정
                // ORDER BY 미지정 시 ROWNUM 기반 서브쿼리로 폴백
                sql = new StringBuilder(
                    "SELECT * FROM (SELECT a.*, ROWNUM rn FROM (SELECT * FROM " + tbl + ") a WHERE ROWNUM <= ?)  WHERE rn > ?"
                );
            }
            List<Map<String, Object>> rows;
            if (safeSort != null) {
                // ORDER BY 있을 때: OFFSET ... ROWS FETCH NEXT ... ROWS ONLY
                sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
                rows = jdbcTemplate.queryForList(sql.toString(), offset, size);
            } else {
                // ORDER BY 없을 때: ROWNUM 서브쿼리 (offset+size 로 상단 자르고, rn>offset 으로 하단 자름)
                rows = jdbcTemplate.queryForList(sql.toString(), offset + size, offset);
            }

            return Map.of(
                "ok", true,
                "table", tbl,
                "total", total != null ? total : 0,
                "page", page,
                "size", size,
                "rows", rows
            );
        } catch (Exception e) {
            log.error("getData error: {}", e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 단건 상세 조회 ─────────────────────────────────────────────
    public Map<String, Object> getDetail(String table, Map<String, String> params) {
        String tbl = validateTable(table);
        // params에서 WHERE 조건 구성
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        params.forEach((k, v) -> {
            if (k.matches("[A-Za-z0-9_]+")) {
                conditions.add(k + " = ?");
                args.add(v);
            }
        });
        if (conditions.isEmpty()) return Map.of("ok", false, "error", "조건 필수");

        String sql = "SELECT * FROM " + tbl + " WHERE " + String.join(" AND ", conditions);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
            return Map.of("ok", true, "row", rows.isEmpty() ? null : rows.get(0));
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 임의 SQL 실행 (SELECT 전용) ────────────────────────────────
    public Map<String, Object> executeSql(String sql) {
        if (sql == null || sql.isBlank()) return Map.of("ok", false, "error", "SQL 필수");
        if (!SELECT_ONLY.matcher(sql.trim()).matches()) {
            return Map.of("ok", false, "error", "SELECT/WITH 문만 허용됩니다");
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            return Map.of("ok", true, "rows", rows, "count", rows.size());
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 테이블 통계 ────────────────────────────────────────────────
    public Map<String, Object> getStats() {
        List<Map<String, Object>> stats = new ArrayList<>();
        for (String tbl : ALLOWED_TABLES) {
            // ORACLE_WMS_TABLES 소속이면 KNRAWMS. 접두어 붙여서 쿼리
            String qualifiedTbl = ORACLE_WMS_TABLES.contains(tbl)
                ? "KNRAWMS." + tbl : tbl;
            try {
                Long cnt = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + qualifiedTbl, Long.class);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("table", tbl);          // UI에는 원시 테이블명 표시
                row.put("rows", cnt != null ? cnt : 0);
                stats.add(row);
            } catch (Exception e) {
                log.debug("Stats COUNT error for {}: {}", qualifiedTbl, e.getMessage());
            }
        }
        return Map.of("ok", true, "stats", stats);
    }

    // ── Row INSERT ─────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> insertRow(String table, Map<String, Object> body) {
        String tbl = validateTable(table);
        if (READONLY_TABLES.contains(tbl)) return Map.of("ok", false, "error", "읽기 전용 테이블");

        body.remove("_csrf");
        if (body.isEmpty()) return Map.of("ok", false, "error", "데이터 필수");

        List<String> cols = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        body.forEach((k, v) -> {
            if (k.matches("[A-Za-z0-9_]+")) { cols.add(k); vals.add(v); }
        });

        String sql = "INSERT INTO " + tbl + " (" + String.join(",", cols) + ") VALUES ("
                + String.join(",", Collections.nCopies(cols.size(), "?")) + ")";
        try {
            jdbcTemplate.update(sql, vals.toArray());
            return Map.of("ok", true);
        } catch (Exception e) {
            log.error("insertRow error: {}", e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── Row UPDATE ─────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> updateRow(String table, Map<String, Object> body) {
        String tbl = validateTable(table);
        if (READONLY_TABLES.contains(tbl)) return Map.of("ok", false, "error", "읽기 전용 테이블");

        // _pk_* 접두사로 PK 컬럼 구분
        Map<String, Object> pkMap  = new LinkedHashMap<>();
        Map<String, Object> setMap = new LinkedHashMap<>();
        body.forEach((k, v) -> {
            if (k.startsWith("_pk_")) pkMap.put(k.substring(4), v);
            else if (k.matches("[A-Za-z0-9_]+")) setMap.put(k, v);
        });
        if (pkMap.isEmpty() || setMap.isEmpty())
            return Map.of("ok", false, "error", "PK(_pk_*) 및 변경 데이터 필수");

        List<String> setClauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        setMap.forEach((k, v) -> { setClauses.add(k + "=?"); args.add(v); });
        List<String> pkClauses = new ArrayList<>();
        pkMap.forEach((k, v) -> { pkClauses.add(k + "=?"); args.add(v); });

        String sql = "UPDATE " + tbl + " SET " + String.join(",", setClauses)
                + " WHERE " + String.join(" AND ", pkClauses);
        try {
            int affected = jdbcTemplate.update(sql, args.toArray());
            return Map.of("ok", true, "affected", affected);
        } catch (Exception e) {
            log.error("updateRow error: {}", e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── Row DELETE ─────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> deleteRow(String table, Map<String, Object> body) {
        String tbl = validateTable(table);
        if (READONLY_TABLES.contains(tbl)) return Map.of("ok", false, "error", "읽기 전용 테이블");

        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        body.forEach((k, v) -> {
            if (k.matches("[A-Za-z0-9_]+")) { conditions.add(k + "=?"); args.add(v); }
        });
        if (conditions.isEmpty()) return Map.of("ok", false, "error", "조건 필수");

        String sql = "DELETE FROM " + tbl + " WHERE " + String.join(" AND ", conditions);
        try {
            int affected = jdbcTemplate.update(sql, args.toArray());
            return Map.of("ok", true, "affected", affected);
        } catch (Exception e) {
            log.error("deleteRow error: {}", e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 내부 유틸 ──────────────────────────────────────────────────
    /**
     * 테이블명 검증 후 SQL 에 사용할 정규화된 테이블명 반환.
     * Oracle WMS(KNRAWMS 스키마) 소속 → "KNRAWMS.TABLE"
     * 자체 KNRATMS/MariaDB 테이블 → "TABLE"
     *
     * ※ getSchema() 에서는 DatabaseMetaData API 특성상 schema/table 분리가 필요하므로
     *   이 메서드를 사용하지 않고 내부에서 직접 처리.
     */
    private String validateTable(String table) {
        String upper = table.toUpperCase();
        if (!ALLOWED_TABLES.contains(upper))
            throw new IllegalArgumentException("허용되지 않는 테이블: " + table);
        return ORACLE_WMS_TABLES.contains(upper) ? "KNRAWMS." + upper : upper;
    }
}
