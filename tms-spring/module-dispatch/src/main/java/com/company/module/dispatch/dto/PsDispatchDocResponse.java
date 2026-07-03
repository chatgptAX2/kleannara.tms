package com.company.module.dispatch.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * PS 배차 납품문서 단건 응답 DTO
 * Flask: api_ps_dispatch_search() 결과 row 대응
 */
@Getter
@Builder
public class PsDispatchDocResponse {

    private String shpoky;
    private String svbeln;
    private String shpoit;
    private String skukey;
    private String desc01;
    private String uomkey;
    private Double qtshpo;
    private Double grswgt;
    private Double kgWeight;
    private Double rollCbm;
    private Double boardCbm;
    private Double unitWeight;   // RECDI 기반 단일 롤 중량 (0=미등록)
    private Integer rollCount;   // 원지 롤 수
    private String skug05;
    private String skuType;      // roll | board | other
    private String dptnky;
    private String dptnm;
    private String docdat;
    private String rqshpd;
    private String shpmty;
    private String shpmtyNm;
    private String inch;
    private String grmCond;
    private Boolean dispatched;
    private String lota03;       // 포장타입
}
