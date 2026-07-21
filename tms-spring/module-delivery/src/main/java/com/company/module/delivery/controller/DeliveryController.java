package com.company.module.delivery.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.delivery.dto.*;
import com.company.module.delivery.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 납품처 관리 Controller
 * Flask: /api/delivery/*, /api/route_cost/* 대응
 * URL prefix: /api (nginx: /api/ → Spring)
 * ※ 이중 prefix {"/delivery-api", "/api"} 제거 → 단일 /api prefix로 정리
 *   (SapController 등 다른 /api 컨트롤러와 하위 경로 충돌 없음)
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    /** 납품처 목록 – Flask: GET /api/delivery/list */
    @GetMapping("/delivery/list")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getList(
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String vstel,
            @RequestParam(required = false) String skug05,
            @RequestParam(required = false) String ptnrky,
            @RequestParam(required = false) String q,
            @RequestParam(name = "sort_col", defaultValue = "PTNRKY") String sortCol,
            @RequestParam(name = "sort_dir", defaultValue = "ASC")    String sortDir,
            @RequestParam(required = false) List<String> werks) {

        DeliverySearchRequest req = new DeliverySearchRequest();
        req.setPage(page); req.setSize(size); req.setVstel(vstel);
        req.setSkug05(skug05); req.setPtnrky(ptnrky); req.setQ(q);
        req.setSortCol(sortCol); req.setSortDir(sortDir);
        req.setWerks(werks);
        return ResponseEntity.ok(ApiResponse.success(deliveryService.getList(req)));
    }

    /** 납품처 상세 – Flask: GET /api/delivery/detail/<ptnrky> */
    @GetMapping("/delivery/detail/{ptnrky}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDetail(
            @PathVariable String ptnrky,
            @RequestParam(defaultValue = "CT") String ptnrty,
            @RequestParam(defaultValue = "KN") String ownrky) {
        return ResponseEntity.ok(ApiResponse.success(deliveryService.getDetail(ptnrky, ptnrty, ownrky)));
    }

    /** 납품처 저장 – Flask: POST /api/delivery/save */
    @PostMapping("/delivery/save")
    public ResponseEntity<ApiResponse<Object>> save(@RequestBody DeliverySaveRequest req) {
        String action = deliveryService.saveDetail(req);
        return ResponseEntity.ok(ApiResponse.success(Map.of("ok", true, "action", action)));
    }

    /** 납품처 삭제 – Flask: POST /api/delivery/delete */
    @PostMapping("/delivery/delete")
    public ResponseEntity<ApiResponse<Object>> delete(@RequestBody Map<String, String> body) {
        deliveryService.deleteDetail(
            body.get("PTNRKY"),
            body.getOrDefault("PTNRTY", "CT"),
            body.getOrDefault("OWNRKY", "KN")
        );
        return ResponseEntity.ok(ApiResponse.success(Map.of("ok", true)));
    }

    /** 운송비 검색 – Flask: GET /api/route_cost/search
     *  반환: { rows, total, carclasses } */
    @GetMapping("/route_cost/search")
    public ResponseEntity<ApiResponse<Map<String, Object>>> searchRouteCost(
            @RequestParam(required = false) String wareky,
            @RequestParam(required = false) String ptnrky,
            @RequestParam(required = false) String carclass) {
        return ResponseEntity.ok(ApiResponse.success(deliveryService.searchRouteCost(wareky, ptnrky, carclass)));
    }

    /** 운송비 피벗 – Flask: GET /api/route_cost/pivot */
    @GetMapping("/route_cost/pivot")
    public ResponseEntity<ApiResponse<Map<String, Object>>> pivotRouteCost(
            @RequestParam(required = false) String wareky,
            @RequestParam(required = false) String ptnrky,
            @RequestParam(required = false) String carclass) {
        return ResponseEntity.ok(ApiResponse.success(deliveryService.pivotRouteCost(wareky, ptnrky, carclass)));
    }
}
