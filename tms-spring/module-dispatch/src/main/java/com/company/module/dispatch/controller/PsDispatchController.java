package com.company.module.dispatch.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.dispatch.dto.*;
import com.company.module.dispatch.service.PsDispatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PS 배차 REST Controller
 * Flask: /api/ps-dispatch/* 대응
 * URL prefix: /dispatch-api/ps-dispatch
 */
@RestController
@RequestMapping("/dispatch-api/ps-dispatch")
@RequiredArgsConstructor
public class PsDispatchController {

    private final PsDispatchService psDispatchService;

    /**
     * 배차용 납품문서 조회
     * Flask: GET /api/ps-dispatch/search
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<PsDispatchDocResponse>>> search(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String dptnky,
            @RequestParam(required = false) String shpoky,
            @RequestParam(required = false) List<String> shpmty,
            @RequestParam(defaultValue = "all") String status) {

        PsDispatchSearchRequest req = new PsDispatchSearchRequest();
        req.setDateFrom(dateFrom);
        req.setDateTo(dateTo);
        req.setDptnky(dptnky);
        req.setShpoky(shpoky);
        req.setShpmty(shpmty);
        req.setStatus(status);

        List<PsDispatchDocResponse> rows = psDispatchService.searchDocs(req);
        return ResponseEntity.ok(ApiResponse.success(rows));
    }

    /**
     * 배차 저장 (자동/수기/드래그 공통)
     * Flask: POST /api/ps-dispatch/save
     */
    @PostMapping("/save")
    public ResponseEntity<ApiResponse<Object>> save(
            @Valid @RequestBody PsDispatchSaveRequest req) {

        List<String> saved = psDispatchService.saveDispatch(req);
        return ResponseEntity.ok(ApiResponse.created(
            java.util.Map.of("saved", saved.size(), "dispatch_nos", saved)
        ));
    }

    /**
     * 저장된 배차 목록 조회
     * Flask: GET /api/ps-dispatch/list
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<PsDispatchListResponse>>> list(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String dptnky,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dispatchNo) {

        PsDispatchListRequest req = new PsDispatchListRequest();
        req.setDateFrom(dateFrom);
        req.setDateTo(dateTo);
        req.setDptnky(dptnky);
        req.setStatus(status);
        req.setDispatchNo(dispatchNo);

        List<PsDispatchListResponse> rows = psDispatchService.getList(req);
        return ResponseEntity.ok(ApiResponse.success(rows));
    }

    /**
     * 배차 확정 (DRAFT → CONFIRMED)
     * Flask: POST /api/ps-dispatch/confirm
     */
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<Object>> confirm(
            @Valid @RequestBody PsDispatchConfirmRequest req) {

        int cnt = psDispatchService.confirmDispatch(req);
        return ResponseEntity.ok(ApiResponse.success(
            java.util.Map.of("confirmed", cnt)
        ));
    }
}
