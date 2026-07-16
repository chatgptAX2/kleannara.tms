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
@RequestMapping({"/dispatch-api/ps-dispatch", "/api/ps-dispatch"})
@RequiredArgsConstructor
public class PsDispatchController {

    private final PsDispatchService psDispatchService;

    /**
     * 배차용 납품문서 조회
     * Flask: GET /api/ps-dispatch/search
     * JS 파라미터: date_from / date_to (또는 dateFrom / dateTo)
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<PsDispatchDocResponse>>> search(
            @RequestParam(name = "dateFrom",  required = false) String dateFrom,
            @RequestParam(name = "date_from", required = false) String dateFromAlt,
            @RequestParam(name = "dateTo",    required = false) String dateTo,
            @RequestParam(name = "date_to",   required = false) String dateToAlt,
            @RequestParam(required = false) String dptnky,
            @RequestParam(required = false) String shpoky,
            @RequestParam(required = false) List<String> shpmty,
            @RequestParam(required = false) String wareky,
            @RequestParam(required = false) String skug05,
            @RequestParam(defaultValue = "all") String status) {

        PsDispatchSearchRequest req = new PsDispatchSearchRequest();
        req.setDateFrom(dateFrom != null ? dateFrom : dateFromAlt);
        req.setDateTo(dateTo   != null ? dateTo   : dateToAlt);
        req.setDptnky(dptnky);
        req.setShpoky(shpoky);
        req.setShpmty(shpmty);
        req.setWareky(wareky);
        req.setSkug05(skug05);
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
