package com.company.module.dispatch.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.dispatch.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * TMS 대시보드 REST Controller
 * GET /api/dashboard/transport  - 운송현황 (일자별 배차대기/완료, 출하유형별 분포)
 * GET /api/dashboard/efficiency - 운송효율성 (적재율, 권역별 물량, 평균 효율)
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 운송현황 데이터
     * - 최근 7일 일자별 배차대기/배차완료 차량대수 및 비율
     * - 출하유형별 차량대수 및 물량 분포 (MATERIAL_TYPE: ROLL/BOARD/OTHER)
     */
    @GetMapping("/transport")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTransportStatus(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getTransportStatus(dateFrom, dateTo)));
    }

    /**
     * 운송효율성 데이터
     * - 차량별 적재율 분포 (0~20%, 20~40%, ... 80~100%)
     * - 권역별 물량 분포 (차량대수 + 총중량)
     * - 평균 적재효율 (전체/ROLL/BOARD)
     */
    @GetMapping("/efficiency")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEfficiency(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getEfficiency(dateFrom, dateTo)));
    }

    /**
     * 대시보드 요약 KPI (상단 카드용)
     * - 오늘 배차 대기/완료/취소 건수
     * - 이번주 총 운송 차량수
     * - 전일 대비 증감률
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getSummary()));
    }
}
