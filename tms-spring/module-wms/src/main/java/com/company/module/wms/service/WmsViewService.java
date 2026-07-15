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
 * WMS 뷰어 서비스 — JDBC 기반 동적 쿼리
 *
 * ■ DataSource 이중 라우팅
 *   - ORACLE_WMS_TABLES (KNRAWMS 스키마) → wmsJdbcTemplate / wmsDataSource (Oracle)
 *   - 나머지 Oracle TMS 자체 테이블       → tmsJdbcTemplate / tmsDataSource (Oracle KNRAWMS)
 *
 * ■ Oracle 테이블 접근 규칙
 *   KNRATMS 계정으로 접속 → KNRAWMS 스키마 테이블은 반드시 "KNRAWMS.TABLE" 형식 사용
 */
@Slf4j
@Service
public class WmsViewService {

    // ── Oracle WMS DataSource ─────────────────────────────────────
    private final JdbcTemplate wmsJdbc;
    private final DataSource   wmsDataSource;

    // ── MariaDB TMS DataSource ────────────────────────────────────
    private final JdbcTemplate tmsJdbc;
    private final DataSource   tmsDataSource;

    public WmsViewService(
            @Qualifier("wmsJdbcTemplate") JdbcTemplate wmsJdbc,
            @Qualifier("wmsDataSource")   DataSource   wmsDataSource,
            @Qualifier("tmsJdbcTemplate") JdbcTemplate tmsJdbc,
            @Qualifier("tmsDataSource")   DataSource   tmsDataSource) {
        this.wmsJdbc      = wmsJdbc;
        this.wmsDataSource = wmsDataSource;
        this.tmsJdbc      = tmsJdbc;
        this.tmsDataSource = tmsDataSource;
    }

    // ── 허용 테이블 목록 ──────────────────────────────────────────

    /**
     * Oracle KNRAWMS 스키마 소속 테이블 — wmsJdbcTemplate / wmsDataSource 사용
     * 조회 SQL 에 반드시 "KNRAWMS." 접두어 필요 (KNRATMS 계정 기준)
     */
    private static final Set<String> ORACLE_WMS_TABLES = new HashSet<>(Arrays.asList(
        "CMCDM", "CMCDV", "WAHMA", "SKUMA", "BZPTN", "MEASI",
        "SHPDH", "SHPDI", "IFWMS113", "RECDI", "BZPTN_DETAIL"
    ));

    /**
     * Oracle KNRAWMS TMS 자체 테이블 — tmsJdbcTemplate / tmsDataSource 사용
     */
    private static final Set<String> ORACLE_TMS_TABLES = new HashSet<>(Arrays.asList(
        "VHCMA", "ROUTE_COST", "DS_VEHICLE",
        "PS_DISPATCH_H", "PS_DISPATCH_D", "PS_DISPATCH_SPLIT",
        "DS_INCH12", "DS_INCH3",
        "DS_DISPATCH_OBJECTIVE", "DS_DISPATCH_CONST_SET", "DS_DISPATCH_CONST_SET_ITEM",
        "DS_DISPATCH_PROFILE", "DS_DISPATCH_CONSTRAINT", "DS_DISPATCH_CONST",
        "DOC_FOLDER", "DOC_FILE"
    ));

    private static final Set<String> ALLOWED_TABLES = new LinkedHashSet<>(Arrays.asList(
        // Oracle WMS (KNRAWMS 스키마)
        "CMCDM", "CMCDV", "WAHMA", "SKUMA", "BZPTN", "MEASI",
        "SHPDH", "SHPDI", "IFWMS113", "BZPTN_DETAIL", "RECDI",
        // Oracle KNRAWMS TMS 자체 테이블
        "VHCMA", "ROUTE_COST", "DS_VEHICLE",
        "PS_DISPATCH_H", "PS_DISPATCH_D", "PS_DISPATCH_SPLIT",
        "DS_INCH12", "DS_INCH3",
        "DS_DISPATCH_OBJECTIVE", "DS_DISPATCH_CONST_SET", "DS_DISPATCH_CONST_SET_ITEM",
        "DS_DISPATCH_PROFILE", "DS_DISPATCH_CONSTRAINT", "DS_DISPATCH_CONST",
        "DOC_FOLDER", "DOC_FILE"
    ));

    // 읽기 전용 테이블 (INSERT/UPDATE/DELETE 불가)
    private static final Set<String> READONLY_TABLES = new HashSet<>(Arrays.asList(
        "SHPDH", "SHPDI", "IFWMS113"
    ));

    // SELECT 전용 SQL 패턴 체크
    private static final Pattern SELECT_ONLY = Pattern.compile(
        "^\\s*(SELECT|WITH)\\s+.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // ── 라우팅 헬퍼 ───────────────────────────────────────────────

    /** 테이블에 맞는 JdbcTemplate 반환 */
    private JdbcTemplate jdbc(String upperTable) {
        return ORACLE_WMS_TABLES.contains(upperTable) ? wmsJdbc : tmsJdbc;
    }

    /** 테이블에 맞는 DataSource 반환 */
    private DataSource ds(String upperTable) {
        return ORACLE_WMS_TABLES.contains(upperTable) ? wmsDataSource : tmsDataSource;
    }

    // ── 테이블 목록 ────────────────────────────────────────────────
    public Map<String, Object> getTables() {
        List<Map<String, Object>> tables = new ArrayList<>();
        for (String tbl : ALLOWED_TABLES) {
            // Oracle WMS 테이블은 KNRAWMS. 접두어 필요
            String qualifiedTbl = ORACLE_WMS_TABLES.contains(tbl)
                ? "KNRAWMS." + tbl : tbl;
            try {
                Long cnt = jdbc(tbl).queryForObject(
                    "SELECT COUNT(*) FROM " + qualifiedTbl, Long.class
                );
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", tbl);
                row.put("rows", cnt != null ? cnt : 0);
                tables.add(row);
            } catch (Exception e) {
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

        boolean isOracleWms = ORACLE_WMS_TABLES.contains(upper);
        // Oracle JDBC: schema/table 분리 필수 ("KNRAWMS.CMCDM" 형식 미지원)
        // MariaDB: schema=null 로 처리
        String schemaParam  = isOracleWms ? "KNRAWMS" : null;
        String tableParam   = upper;
        String qualifiedTbl = isOracleWms ? "KNRAWMS." + upper : upper;

        List<Map<String, Object>> cols = new ArrayList<>();
        try (Connection conn = ds(upper).getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            // PK 컬럼 조회
            Set<String> pkCols = new HashSet<>();
            try (ResultSet pk = meta.getPrimaryKeys(null, schemaParam, tableParam)) {
                while (pk.next()) pkCols.add(pk.getString("COLUMN_NAME"));
            }
            // 컬럼 목록 조회
            try (ResultSet rs = meta.getColumns(null, schemaParam, tableParam, null)) {
                while (rs.next()) {
                    cols.add(buildColMap(rs, pkCols));
                }
            }
            // MariaDB 폴백: schema 파라미터로 빈 결과가 나오면 null 로 재시도
            if (cols.isEmpty() && !isOracleWms) {
                try (ResultSet rs = meta.getColumns(null, null, tableParam, null)) {
                    while (rs.next()) {
                        cols.add(buildColMap(rs, pkCols));
                    }
                }
            }
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
        return Map.of("ok", true, "table", qualifiedTbl, "columns", cols);
    }

    // ── 테이블 데이터 조회 (페이징, 검색, 정렬) ──────────────────────
    public Map<String, Object> getData(String table, int page, int size,
                                       String search, String sortCol, String sortDir) {
        String upper = table.toUpperCase();
        if (!ALLOWED_TABLES.contains(upper))
            throw new IllegalArgumentException("허용되지 않는 테이블: " + table);

        boolean isOracle  = true; // TMS/WMS 모두 Oracle KNRAWMS
        String tbl        = "KNRAWMS." + upper;  // TMS/WMS 모두 Oracle KNRAWMS 동일 계정
        int offset         = Math.max(0, (page - 1)) * size;
        String safeOrder   = "ASC".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
        String safeSort    = (sortCol != null && sortCol.matches("[A-Za-z0-9_]+")) ? sortCol : null;
        boolean hasSearch  = (search != null && !search.isBlank());

        // ── ① 스키마 메타 먼저 조회 (검색 컬럼 목록 필요) ──────────────
        List<String> columns    = new ArrayList<>();
        List<String> pkCols     = new ArrayList<>();
        Map<String, String> labels = new LinkedHashMap<>();
        List<String> searchCols = new ArrayList<>();

        try (Connection conn = ds(upper).getConnection()) {
            DatabaseMetaData meta  = conn.getMetaData();
            String schemaParam     = isOracle ? "KNRAWMS" : null;

            Set<String> pkSet = new LinkedHashSet<>();
            try (ResultSet pk = meta.getPrimaryKeys(null, schemaParam, upper)) {
                while (pk.next()) pkSet.add(pk.getString("COLUMN_NAME"));
            }

            try (ResultSet rs = meta.getColumns(null, schemaParam, upper, null)) {
                collectColMeta(rs, columns, labels, searchCols);
            }

            // MariaDB 폴백
            if (columns.isEmpty() && !isOracle) {
                try (ResultSet rs = meta.getColumns(null, null, upper, null)) {
                    collectColMeta(rs, columns, labels, searchCols);
                }
                if (pkSet.isEmpty()) {
                    try (ResultSet pk = meta.getPrimaryKeys(null, null, upper)) {
                        while (pk.next()) pkSet.add(pk.getString("COLUMN_NAME"));
                    }
                }
            }
            pkCols.addAll(pkSet);

        } catch (Exception metaEx) {
            log.warn("getData schema meta error [{}]: {}", tbl, metaEx.getMessage());
        }

        // ── ② WHERE LIKE 절 조립 (검색어 + 검색 가능 컬럼 존재 시) ───────
        // 검색 대상: searchCols 우선, 없으면 columns 전체 대상
        List<String> targetCols = !searchCols.isEmpty() ? searchCols : columns;
        String whereSql  = "";
        String likeParam = hasSearch ? "%" + search.trim() + "%" : "";
        List<Object> searchArgs = new ArrayList<>();

        if (hasSearch && !targetCols.isEmpty()) {
            List<String> likeClauses = new ArrayList<>();
            for (String col : targetCols) {
                likeClauses.add(col + " LIKE ?");
                searchArgs.add(likeParam);
            }
            whereSql = " WHERE (" + String.join(" OR ", likeClauses) + ")";
        }

        // ── ③ 데이터 조회 ────────────────────────────────────────────────
        try {
            // total COUNT
            String countSql = "SELECT COUNT(*) FROM " + tbl + whereSql;
            Long total = jdbc(upper).queryForObject(countSql, Long.class, searchArgs.toArray());

            List<Map<String, Object>> rows;
            if (isOracle) {
                // Oracle: ROWNUM 서브쿼리 페이징 (WHERE 절 포함)
                if (safeSort != null) {
                    String sql = "SELECT * FROM " + tbl + whereSql
                        + " ORDER BY " + safeSort + " " + safeOrder
                        + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
                    List<Object> args = new ArrayList<>(searchArgs);
                    args.add(offset); args.add(size);
                    rows = wmsJdbc.queryForList(sql, args.toArray());
                } else {
                    // ORDER BY 없으면 ROWNUM 서브쿼리
                    String inner = "SELECT * FROM " + tbl + whereSql;
                    String sql   = "SELECT * FROM (SELECT a.*, ROWNUM rn FROM"
                        + " (" + inner + ") a WHERE ROWNUM <= ?) WHERE rn > ?";
                    List<Object> args = new ArrayList<>(searchArgs);
                    args.add(offset + size); args.add(offset);
                    rows = wmsJdbc.queryForList(sql, args.toArray());
                }
            } else {
                // Oracle TMS 테이블: OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tbl).append(whereSql);
                if (safeSort != null) {
                    sql.append(" ORDER BY ").append(safeSort).append(" ").append(safeOrder);
                }
                sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
                List<Object> args = new ArrayList<>(searchArgs);
                args.add(offset); args.add(size);
                rows = tmsJdbc.queryForList(sql.toString(), args.toArray());
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok",          true);
            result.put("table",       tbl);
            result.put("total",       total != null ? total : 0);
            result.put("page",        page);
            result.put("size",        size);
            result.put("rows",        rows);
            result.put("columns",     columns);
            result.put("pk_cols",     pkCols);
            result.put("labels",      labels);
            result.put("search_cols", searchCols);
            return result;

        } catch (Exception e) {
            log.error("getData error [{}]: {}", tbl, e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    /** ResultSet → columns / labels / searchCols 수집 헬퍼 */
    private void collectColMeta(ResultSet rs,
                                 List<String> columns,
                                 Map<String, String> labels,
                                 List<String> searchCols) throws SQLException {
        while (rs.next()) {
            String colName  = rs.getString("COLUMN_NAME");
            String typeName = rs.getString("TYPE_NAME");
            String remark   = rs.getString("REMARKS");

            columns.add(colName);
            labels.put(colName, (remark != null && !remark.isBlank()) ? remark : colName);
            if (typeName != null) {
                String t = typeName.toUpperCase();
                if (t.contains("VARCHAR") || t.contains("CHAR") || t.contains("TEXT")) {
                    searchCols.add(colName);
                }
            }
        }
    }

    // ── 단건 상세 조회 ─────────────────────────────────────────────
    public Map<String, Object> getDetail(String table, Map<String, String> params) {
        String upper = table.toUpperCase();
        if (!ALLOWED_TABLES.contains(upper))
            throw new IllegalArgumentException("허용되지 않는 테이블: " + table);

        String tbl = ORACLE_WMS_TABLES.contains(upper) ? "KNRAWMS." + upper : upper;
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
            List<Map<String, Object>> rows = jdbc(upper).queryForList(sql, args.toArray());
            return Map.of("ok", true, "row", rows.isEmpty() ? null : rows.get(0));
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 임의 SQL 실행 (SELECT 전용, Oracle wmsJdbc 사용) ───────────
    public Map<String, Object> executeSql(String sql) {
        if (sql == null || sql.isBlank()) return Map.of("ok", false, "error", "SQL 필수");
        if (!SELECT_ONLY.matcher(sql.trim()).matches()) {
            return Map.of("ok", false, "error", "SELECT/WITH 문만 허용됩니다");
        }
        try {
            List<Map<String, Object>> rows = wmsJdbc.queryForList(sql);
            return Map.of("ok", true, "rows", rows, "count", rows.size());
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 테이블 통계 ────────────────────────────────────────────────
    public Map<String, Object> getStats() {
        List<Map<String, Object>> stats = new ArrayList<>();
        for (String tbl : ALLOWED_TABLES) {
            String qualifiedTbl = ORACLE_WMS_TABLES.contains(tbl)
                ? "KNRAWMS." + tbl : tbl;
            try {
                Long cnt = jdbc(tbl).queryForObject(
                    "SELECT COUNT(*) FROM " + qualifiedTbl, Long.class
                );
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("table", tbl);
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
        String upper = table.toUpperCase();
        if (!ALLOWED_TABLES.contains(upper))
            throw new IllegalArgumentException("허용되지 않는 테이블: " + table);
        String tbl = ORACLE_WMS_TABLES.contains(upper) ? "KNRAWMS." + upper : upper;
        if (READONLY_TABLES.contains(upper)) return Map.of("ok", false, "error", "읽기 전용 테이블");

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
            jdbc(upper).update(sql, vals.toArray());
            return Map.of("ok", true);
        } catch (Exception e) {
            log.error("insertRow error [{}]: {}", tbl, e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── Row UPDATE ─────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> updateRow(String table, Map<String, Object> body) {
        String upper = table.toUpperCase();
        if (!ALLOWED_TABLES.contains(upper))
            throw new IllegalArgumentException("허용되지 않는 테이블: " + table);
        String tbl = ORACLE_WMS_TABLES.contains(upper) ? "KNRAWMS." + upper : upper;
        if (READONLY_TABLES.contains(upper)) return Map.of("ok", false, "error", "읽기 전용 테이블");

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
            int affected = jdbc(upper).update(sql, args.toArray());
            return Map.of("ok", true, "affected", affected);
        } catch (Exception e) {
            log.error("updateRow error [{}]: {}", tbl, e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── Row DELETE ─────────────────────────────────────────────────
    @Transactional
    public Map<String, Object> deleteRow(String table, Map<String, Object> body) {
        String upper = table.toUpperCase();
        if (!ALLOWED_TABLES.contains(upper))
            throw new IllegalArgumentException("허용되지 않는 테이블: " + table);
        String tbl = ORACLE_WMS_TABLES.contains(upper) ? "KNRAWMS." + upper : upper;
        if (READONLY_TABLES.contains(upper)) return Map.of("ok", false, "error", "읽기 전용 테이블");

        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        body.forEach((k, v) -> {
            if (k.matches("[A-Za-z0-9_]+")) { conditions.add(k + "=?"); args.add(v); }
        });
        if (conditions.isEmpty()) return Map.of("ok", false, "error", "조건 필수");

        String sql = "DELETE FROM " + tbl + " WHERE " + String.join(" AND ", conditions);
        try {
            int affected = jdbc(upper).update(sql, args.toArray());
            return Map.of("ok", true, "affected", affected);
        } catch (Exception e) {
            log.error("deleteRow error [{}]: {}", tbl, e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    // ── 내부 유틸 ──────────────────────────────────────────────────

    /** ResultSet 한 행 → 컬럼 정보 Map 변환 */
    private Map<String, Object> buildColMap(ResultSet rs, Set<String> pkCols) throws SQLException {
        Map<String, Object> col = new LinkedHashMap<>();
        String colNm = rs.getString("COLUMN_NAME");
        col.put("name",     colNm);
        col.put("type",     rs.getString("TYPE_NAME"));
        col.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
        col.put("pk",       pkCols.contains(colNm));
        col.put("default",  rs.getString("COLUMN_DEF"));
        col.put("size",     rs.getInt("COLUMN_SIZE"));
        col.put("remark",   rs.getString("REMARKS"));
        return col;
    }
}
