package com.company.module.dispatch.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.dispatch.dto.PsReturnDocResponse;
import com.company.module.dispatch.dto.PsReturnSaveRequest;
import com.company.module.dispatch.dto.PsReturnSearchRequest;
import com.company.module.dispatch.service.PsReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 반품 배차 REST Controller (신규)
 * 원천: KNRAWMS.IFWMS103 (반품입고 BWART=131)
 *
 * ※ 기존 PS배차(PsDispatchController) 로직은 전혀 건드리지 않는 신규 컨트롤러.
 *   - 조회: GET /api/ps-return/search → 반품 배차 대상 리스트
 *   URL prefix: /dispatch-api/ps-return, /api/ps-return
 */
@RestController
@RequestMapping({"/dispatch-api/ps-return", "/api/ps-return"})
@RequiredArgsConstructor
public class PsReturnController {

    private final PsReturnService psReturnService;

    /**
     * 반품 배차 대상 납품문서 조회
     * 필수: wareky(거점, 기본1100), skug05(제품군 10/20 단일)
     *       dateFrom/dateTo(입고예정일, 기본 -3~+3, 최대 -30~+7)
     * 선택: lifnr(납품처코드), ebeln(납품문서번호)
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<PsReturnDocResponse>>> search(
            @RequestParam(name = "wareky",    required = false) String wareky,
            @RequestParam(name = "skug05",    required = false) String skug05,
            @RequestParam(name = "dateFrom",  required = false) String dateFrom,
            @RequestParam(name = "date_from", required = false) String dateFromAlt,
            @RequestParam(name = "dateTo",    required = false) String dateTo,
            @RequestParam(name = "date_to",   required = false) String dateToAlt,
            @RequestParam(name = "lifnr",     required = false) String lifnr,
            @RequestParam(name = "ebeln",     required = false) String ebeln) {

        PsReturnSearchRequest req = new PsReturnSearchRequest();
        req.setWareky(wareky);
        req.setSkug05(skug05);
        req.setDateFrom(dateFrom != null ? dateFrom : dateFromAlt);
        req.setDateTo(dateTo     != null ? dateTo   : dateToAlt);
        req.setLifnr(lifnr);
        req.setEbeln(ebeln);

        List<PsReturnDocResponse> rows = psReturnService.searchReturnDocs(req);
        return ResponseEntity.ok(ApiResponse.success(rows));
    }

    /**
     * 반품 배차 저장
     *  - PS_DISPATCH_H INSERT (DISPATCH_TYPE='GR') + PS_DISPATCH_D INSERT
     *  - UPDATE IFWMS103 SET STKNUM=가선적번호(DISPATCH_NO) (저장 시점 실행)
     */
    @PostMapping("/save")
    public ResponseEntity<ApiResponse<Object>> save(
            @Valid @RequestBody PsReturnSaveRequest req) {

        List<String> saved = psReturnService.saveReturnDispatch(req);
        return ResponseEntity.ok(ApiResponse.created(
            java.util.Map.of("saved", saved.size(), "dispatch_nos", saved)
        ));
    }
}
