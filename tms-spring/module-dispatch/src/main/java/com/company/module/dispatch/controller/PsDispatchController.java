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

        // ★ 커밋 후 검증 — SAP선적탭 미조회 근본원인 진단/방어
        //   saveDispatch(tms 트랜잭션) 리턴 시점에는 이미 커밋 완료.
        //   별도 wms 읽기경로(REQUIRES_NEW)로 SHPDI.STDLNR 반영을 재조회하여
        //   "tms 에는 썼는데 wms(SAP탭 읽기경로)에는 0건"인지 즉시 특정한다.
        int wmsVisible = 0;
        try {
            wmsVisible = psDispatchService.verifyStdlnrViaWms(saved);
        } catch (Exception e) {
            // 검증 실패는 저장 자체를 실패로 만들지 않는다(로그만 남김)
        }

        return ResponseEntity.ok(ApiResponse.created(
            java.util.Map.of(
                "saved", saved.size(),
                "dispatch_nos", saved,
                "wms_visible_stdlnr", wmsVisible
            )
        ));
    }

    /**
     * 저장된 배차 목록 조회
     * Flask: GET /api/ps-dispatch/list
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<PsDispatchListResponse>>> list(
            @RequestParam(name = "dateFrom",    required = false) String dateFrom,
            @RequestParam(name = "date_from",   required = false) String dateFromAlt,
            @RequestParam(name = "dateTo",      required = false) String dateTo,
            @RequestParam(name = "date_to",     required = false) String dateToAlt,
            @RequestParam(required = false) String dptnky,
            @RequestParam(required = false) String status,
            @RequestParam(name = "dispatchNo",  required = false) String dispatchNo,
            @RequestParam(name = "dispatch_no", required = false) String dispatchNoAlt) {

        PsDispatchListRequest req = new PsDispatchListRequest();
        req.setDateFrom(dateFrom != null ? dateFrom : dateFromAlt);
        req.setDateTo(dateTo     != null ? dateTo   : dateToAlt);
        req.setDptnky(dptnky);
        req.setStatus(status);
        req.setDispatchNo(dispatchNo != null ? dispatchNo : dispatchNoAlt);

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

    /**
     * 배차 삭제 (선택 배차 물리 삭제 + SHPDI/SHPDH 원복)
     * Flask: POST /api/ps-dispatch/delete
     *   입력 : { dispatch_nos: ["260728001T", ...] }
     *   출력 : { ok, deleted }  (프론트 psdDeleteDispatch / psopDeleteDispatch 대응)
     *
     * ※ 프론트가 {ok, deleted} 를 최상위에서 소비하므로 ApiResponse 로 감싸지 않고
     *    Map 그대로 반환한다. (load-for-edit 와 동일 패턴)
     */
    @PostMapping("/delete")
    public ResponseEntity<java.util.Map<String, Object>> delete(
            @RequestBody java.util.Map<String, Object> body) {

        @SuppressWarnings("unchecked")
        List<String> nos = body.get("dispatch_nos") instanceof List
            ? (List<String>) body.get("dispatch_nos")
            : java.util.Collections.emptyList();

        try {
            int deleted = psDispatchService.deleteDispatch(nos);
            return ResponseEntity.ok(java.util.Map.of(
                "ok", true,
                "deleted", deleted
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(java.util.Map.of(
                "ok", false,
                "error", e.getMessage() == null ? "배차 삭제 실패" : e.getMessage()
            ));
        }
    }

    /**
     * 저장배차 불러오기 (편집용)
     * Flask: POST /api/ps-dispatch/load-for-edit
     *   입력 : { dispatch_nos: ["260728001T", ...] }
     *   출력 : { ok, count, vehicles:[...], search_rows:[...] }  (프론트 psopMergeRestoredVehicles 대응)
     *
     * ※ 프론트가 {ok, vehicles, search_rows} 를 직접 소비하므로 ApiResponse 로 감싸지 않고
     *    Map 그대로 반환한다.
     */
    @PostMapping("/load-for-edit")
    public ResponseEntity<java.util.Map<String, Object>> loadForEdit(
            @RequestBody java.util.Map<String, Object> body) {

        @SuppressWarnings("unchecked")
        List<String> nos = body.get("dispatch_nos") instanceof List
            ? (List<String>) body.get("dispatch_nos")
            : java.util.Collections.emptyList();

        return ResponseEntity.ok(psDispatchService.loadForEdit(nos));
    }
}
