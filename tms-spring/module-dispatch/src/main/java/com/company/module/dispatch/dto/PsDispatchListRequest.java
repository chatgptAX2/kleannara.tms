package com.company.module.dispatch.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 배차 목록 검색 요청 DTO
 * Flask: GET /api/ps-dispatch/list
 */
@Getter
@Setter
public class PsDispatchListRequest {

    private String dateFrom;
    private String dateTo;
    private String dptnky;
    private String status;
    private String dispatchNo;

    public String normalizedDateFrom() {
        return dateFrom == null ? null : dateFrom.replace("-", "");
    }

    public String normalizedDateTo() {
        return dateTo == null ? null : dateTo.replace("-", "");
    }
}
