package com.company.module.dispatch.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.dispatch.service.ShipAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 출고실적 분석 REST Controller
 *
 * <p>PS배차(최적화)의 배차확정(SAP선적)에서 SAP 전송 완료된 출고(납품) 결과를
 * 지역/톤수/일자/출하유형별로 집계하고, 차량·품목 출고 빈도 및 이동평균 기반
 * 출고량 예측(디지털 트윈)을 제공한다. 조회 전용(기존 기능 영향 없음).</p>
 *
 * <ul>
 *   <li>GET /api/ship-analysis/summary   - 요약 KPI</li>
 *   <li>GET /api/ship-analysis/aggregate - 지역/톤수/일자/출하유형 집계</li>
 *   <li>GET /api/ship-analysis/forecast  - 차량/품목 빈도 + 출고량 예측</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ship-analysis")
@RequiredArgsConstructor
public class ShipAnalysisController {

    private final ShipAnalysisService shipAnalysisService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return ResponseEntity.ok(ApiResponse.success(
                shipAnalysisService.getSummary(dateFrom, dateTo)));
    }

    @GetMapping("/aggregate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAggregate(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return ResponseEntity.ok(ApiResponse.success(
                shipAnalysisService.getAggregates(dateFrom, dateTo)));
    }

    @GetMapping("/forecast")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getForecast(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return ResponseEntity.ok(ApiResponse.success(
                shipAnalysisService.getForecast(dateFrom, dateTo)));
    }
}
