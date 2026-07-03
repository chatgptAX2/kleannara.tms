package com.company.module.dispatch.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * PS 배차 납품문서 검색 요청 DTO
 * Flask: GET /api/ps-dispatch/search
 */
@Getter
@Setter
public class PsDispatchSearchRequest {

    /** 납품요청일(FROM) – yyyy-MM-dd 또는 yyyyMMdd */
    private String dateFrom;

    /** 납품요청일(TO) */
    private String dateTo;

    /** 납품처코드/납품처명 LIKE 검색 */
    private String dptnky;

    /** 납품문서번호 / SAP납품문서번호 LIKE 검색 */
    private String shpoky;

    /** 출하유형코드 복수 선택 (201/205/206/208/221/231) */
    private java.util.List<String> shpmty;

    /**
     * 배차상태 필터
     * all | dispatched | undispatched
     */
    private String status = "all";

    /** yyyyMMdd 형식으로 정규화 */
    public String normalizedDateFrom() {
        return dateFrom == null ? null : dateFrom.replace("-", "");
    }

    public String normalizedDateTo() {
        return dateTo == null ? null : dateTo.replace("-", "");
    }
}
