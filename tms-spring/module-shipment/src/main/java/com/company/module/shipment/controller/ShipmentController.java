package com.company.module.shipment.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.shipment.dto.ShipmentFilterOptsResponse;
import com.company.module.shipment.dto.ShipmentSearchRequest;
import com.company.module.shipment.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 출고진행현황 REST Controller
 *
 * URL prefix: /api/shipment (JS fetch URL 기준)
 * Flask 대응:
 *   POST /api/shipment/schedule
 *   GET  /api/shipment/schedule/filter-opts
 */
@RestController
@RequestMapping({"/shipment-api", "/api/shipment"})
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    /**
     * 출고진행현황 목록 조회 (페이징)
     * 검색조건: 창고, 기간, 출고상태, 품목그룹, 플랜트(LOTA02), 키워드
     */
    @PostMapping("/schedule")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSchedule(
            @Valid @RequestBody ShipmentSearchRequest req) {

        Map<String, Object> result = shipmentService.getSchedule(req);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * [PR-C 출고실적] SAP 선적번호(STKNUM) 채번 완료분만 조회 (페이징).
     * 검색조건은 출고예정정보(/schedule)와 동일하며, STKNUM 채번 고정조건만 추가된다.
     */
    @PostMapping("/actual")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getActual(
            @Valid @RequestBody ShipmentSearchRequest req) {

        Map<String, Object> result = shipmentService.getActual(req);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 출고진행현황 검색 필터 옵션 조회
     * 반환: 창고목록, 출고상태목록, 품목그룹목록, 플랜트목록, 최대날짜
     */
    @GetMapping("/schedule/filter-opts")
    public ResponseEntity<ApiResponse<ShipmentFilterOptsResponse>> getFilterOpts() {
        ShipmentFilterOptsResponse opts = shipmentService.getFilterOpts();
        return ResponseEntity.ok(ApiResponse.success(opts));
    }
}
