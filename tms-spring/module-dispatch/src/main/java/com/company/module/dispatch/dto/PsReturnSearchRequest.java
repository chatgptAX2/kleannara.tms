package com.company.module.dispatch.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 반품 배차 대상 조회 요청 DTO (KNRAWMS.IFWMS103 기반)
 * 신규 — 기존 PsDispatchSearchRequest 는 건드리지 않는다.
 */
@Getter
@Setter
public class PsReturnSearchRequest {

    /** 거점코드 (필수, 기본 1100) */
    private String wareky;

    /** 제품군 (필수, 단일선택: 10=제지, 20=생활) */
    private String skug05;

    /** 입고예정일 FROM (yyyy-MM-dd 또는 yyyyMMdd) */
    private String dateFrom;

    /** 입고예정일 TO (yyyy-MM-dd 또는 yyyyMMdd) */
    private String dateTo;

    /** 납품처코드 (선택, LIFNR 정확일치) */
    private String lifnr;

    /** 납품문서번호 (선택, EBELN 정확일치) */
    private String ebeln;
}
