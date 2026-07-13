package com.company.module.wms.controller;

import com.company.module.wms.service.WmsViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * WMS 뷰어 REST Controller
 * Flask: /api/tables, /api/schema/<t>, /api/data/<t>, /api/detail/<t>, /api/sql, /api/stats
 *        /api/row/* CRUD
 * URL prefix: /api (Flask 경로 그대로 유지)
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WmsViewController {

    private final WmsViewService wmsViewService;

    /** 테이블 목록 */
    @GetMapping("/tables")
    public ResponseEntity<Map<String, Object>> getTables() {
        return ResponseEntity.ok(wmsViewService.getTables());
    }

    /** 테이블 스키마 (컬럼 목록) */
    @GetMapping("/schema/{table}")
    public ResponseEntity<Map<String, Object>> getSchema(@PathVariable String table) {
        return ResponseEntity.ok(wmsViewService.getSchema(table));
    }

    /** 테이블 데이터 조회 (페이징, 검색, 정렬) */
    @GetMapping("/data/{table}")
    public ResponseEntity<Map<String, Object>> getData(
            @PathVariable String table,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(name = "q", required = false) String search,
            @RequestParam(required = false) String sort_col,
            @RequestParam(required = false) String sort_dir) {
        return ResponseEntity.ok(wmsViewService.getData(table, page, size, search, sort_col, sort_dir));
    }

    /** 단건 상세 조회 */
    @GetMapping("/detail/{table}")
    public ResponseEntity<Map<String, Object>> getDetail(
            @PathVariable String table,
            @RequestParam Map<String, String> params) {
        return ResponseEntity.ok(wmsViewService.getDetail(table, params));
    }

    /** 임의 SQL 실행 (SELECT만 허용) */
    @PostMapping("/sql")
    public ResponseEntity<Map<String, Object>> executeSql(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(wmsViewService.executeSql(body.get("sql")));
    }

    /** 테이블 통계 */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(wmsViewService.getStats());
    }

    // ── 범용 Row CRUD ─────────────────────────────────────────────

    /** Row 스키마 (INSERT/UPDATE 폼용) */
    @GetMapping("/row/schema/{table}")
    public ResponseEntity<Map<String, Object>> rowSchema(@PathVariable String table) {
        return ResponseEntity.ok(wmsViewService.getSchema(table));
    }

    /** Row 단건 조회 */
    @GetMapping("/row/get/{table}")
    public ResponseEntity<Map<String, Object>> rowGet(
            @PathVariable String table,
            @RequestParam Map<String, String> params) {
        return ResponseEntity.ok(wmsViewService.getDetail(table, params));
    }

    /** Row INSERT */
    @PostMapping("/row/insert/{table}")
    public ResponseEntity<Map<String, Object>> rowInsert(
            @PathVariable String table,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(wmsViewService.insertRow(table, body));
    }

    /** Row UPDATE */
    @PostMapping("/row/update/{table}")
    public ResponseEntity<Map<String, Object>> rowUpdate(
            @PathVariable String table,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(wmsViewService.updateRow(table, body));
    }

    /** Row DELETE */
    @PostMapping("/row/delete/{table}")
    public ResponseEntity<Map<String, Object>> rowDelete(
            @PathVariable String table,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(wmsViewService.deleteRow(table, body));
    }
}
