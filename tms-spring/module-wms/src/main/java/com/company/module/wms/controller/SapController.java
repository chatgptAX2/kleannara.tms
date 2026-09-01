package com.company.module.wms.controller;

import com.company.module.wms.service.SapRfcService;
import com.company.module.wms.service.SapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SAP 연동 Controller
 * Flask: /api/ps-sap/* → /api/ps-sap/*
 * /api/delivery/shppoint (납품처 출고예정)
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SapController {

    private final SapService    sapService;
    private final SapRfcService sapRfcService;

    // ── SAP 연결 테스트 ────────────────────────────────────────────
    @GetMapping("/ps-sap/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(sapRfcService.ping());
    }

    // ── 납품처 출고예정 포인트 조회 ────────────────────────────────
    @GetMapping("/delivery/shppoint")
    public ResponseEntity<Map<String, Object>> shppoint(
            @RequestParam(required = false) String wareky,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return ResponseEntity.ok(sapService.shppoint(wareky, dateFrom, dateTo));
    }

    // ── SAP 선적 목록 ──────────────────────────────────────────────
    @PostMapping("/ps-sap/list")
    public ResponseEntity<Map<String, Object>> sapList(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(sapService.sapList(body));
    }

    // ── SAP 선적 진단 (배차저장 선적번호 미조회 원인 확인) ──────────
    //   브라우저에서: GET /api/ps-sap/diag?stdlnr=260810007T
    //   SHPDI 실제 저장 여부 / SHPDH 조인 생존 여부 / RQSHPD 값을 JSON 으로 반환
    @GetMapping("/ps-sap/diag")
    public ResponseEntity<Map<String, Object>> sapDiag(@RequestParam String stdlnr) {
        return ResponseEntity.ok(sapRfcService.sapDiag(stdlnr));
    }

    // ── SAP 선적 아이템 ────────────────────────────────────────────
    @PostMapping("/ps-sap/items")
    public ResponseEntity<Map<String, Object>> sapItems(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(sapService.sapItems(body));
    }

    // ── SAP 서류 목록 ──────────────────────────────────────────────
    @PostMapping("/ps-sap/docs")
    public ResponseEntity<Map<String, Object>> sapDocs(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(sapService.sapDocs(body));
    }

    // ── SAP 차량 검색 ──────────────────────────────────────────────
    @PostMapping("/ps-sap/vehicle-search")
    public ResponseEntity<Map<String, Object>> vehicleSearch(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(sapService.vehicleSearch(body));
    }

    // ── SAP 차량 배정 ──────────────────────────────────────────────
    @PostMapping("/ps-sap/assign-vehicle")
    public ResponseEntity<Map<String, Object>> assignVehicle(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(sapService.assignVehicle(body));
    }

    // ── SAP 선적 생성 ──────────────────────────────────────────────
    @PostMapping("/ps-sap/shipment-create")
    public ResponseEntity<Map<String, Object>> shipmentCreate(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(sapService.shipmentCreate(body));
    }

    // ── SAP 선적 삭제 ──────────────────────────────────────────────
    @PostMapping("/ps-sap/shipment-delete")
    public ResponseEntity<Map<String, Object>> shipmentDelete(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(sapService.shipmentDelete(body));
    }

    // ── 배차 삭제 (선택 가선적번호의 가배차 이력 삭제 → 재배차 복원) ──
    @PostMapping("/ps-sap/delete")
    public ResponseEntity<Map<String, Object>> sapDelete(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(sapService.sapDelete(body));
    }

    // ── ps-dispatch 추가 API (기존 Spring Boot 모듈 미구현 부분) ──
    // NOTE: GET /ps-dispatch/search 는 PsDispatchController 에서 처리 (중복 제거)

    @PostMapping("/ps-dispatch/auto")
    public ResponseEntity<Map<String, Object>> psAuto(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(sapService.psAuto(body));
    }

    // NOTE: POST /ps-dispatch/load-for-edit 는 PsDispatchController 에서 처리
    //       (실제 운영 스키마 DISPATCH_NO 기반, dispatch_nos 입력 / vehicles·search_rows 반환)

    @PostMapping("/ps-dispatch/delete")
    public ResponseEntity<Map<String, Object>> psDelete(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(sapService.psDelete(body));
    }

    @PostMapping("/ps-dispatch/split")
    public ResponseEntity<Map<String, Object>> psSplit(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(sapService.psSplit(body));
    }

    @PostMapping("/ps-dispatch/update-item")
    public ResponseEntity<Map<String, Object>> psUpdateItem(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(sapService.psUpdateItem(body));
    }

    @PostMapping("/ps-dispatch/create-manual")
    public ResponseEntity<Map<String, Object>> psCreateManual(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(sapService.psCreateManual(body));
    }
}
