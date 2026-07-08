package com.company.module.wms.controller;

import com.company.module.wms.service.CommonCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 공통코드 + 물류센터 + 출고문서 Controller
 * Flask: /api/codes/<cmcdky>, /api/wahma/*, /api/shpdh/detail/<shpoky>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommonCodeController {

    private final CommonCodeService commonCodeService;

    // ── 공통코드 ──────────────────────────────────────────────────

    @GetMapping("/codes/{cmcdky}")
    public ResponseEntity<Map<String, Object>> getCodes(@PathVariable String cmcdky) {
        return ResponseEntity.ok(commonCodeService.getCodes(cmcdky));
    }

    // ── 물류센터 (WAHMA) ──────────────────────────────────────────

    @GetMapping("/wahma/list")
    public ResponseEntity<Map<String, Object>> wahmaList() {
        return ResponseEntity.ok(commonCodeService.wahmaList());
    }

    @GetMapping("/wahma/detail/{wareky}")
    public ResponseEntity<Map<String, Object>> wahmaDetail(@PathVariable String wareky) {
        return ResponseEntity.ok(commonCodeService.wahmaDetail(wareky));
    }

    @PostMapping("/wahma/save")
    public ResponseEntity<Map<String, Object>> wahmaSave(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(commonCodeService.wahmaSave(body));
    }

    @PostMapping("/wahma/delete")
    public ResponseEntity<Map<String, Object>> wahmaDelete(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(commonCodeService.wahmaDelete((String) body.get("WAREKY")));
    }

    // ── 출고문서 헤더 상세 ────────────────────────────────────────

    @GetMapping("/shpdh/detail/{shpoky}")
    public ResponseEntity<Map<String, Object>> shpdhDetail(@PathVariable String shpoky) {
        return ResponseEntity.ok(commonCodeService.shpdhDetail(shpoky));
    }
}
