package com.company.module.dispatch.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 저장된 배차 목록 단건 응답 DTO
 * Flask: api_ps_dispatch_list() 결과 row 대응
 */
@Getter
@Builder
public class PsDispatchListResponse {

    private String dispatchNo;
    private String dispatchDt;
    private String rqshpd;
    private String dptnky;
    private String dptnm;
    private String cartype;
    private String status;
    private Double totalKg;
    private Integer totalCnt;
    private String note;
    private String credat;
    private Double loadTon;
    private Double loadKg;      // LOAD_TON × 1000 (null = DS_VEHICLE 미등록)
    private Integer rollCount;  // 원지 롤 수 합산

    /** 배차 아이템 상세 */
    private List<ItemDetail> items;

    @Getter
    @Builder
    public static class ItemDetail {
        private Long   itemId;
        private Integer seq;
        private String shpoky;
        private String shpoit;
        private String skukey;
        private String desc01;
        private Double qtshpo;
        private String uomkey;
        private String dptnky;
        private String dptnm;
        private Integer isSplit;
        private String orgShpoky;
        private String orgShpoit;
        private Double grswgt;
        private Double kgWeight;
        private Double unitWeight;   // RECDI 기반 단일 롤 중량
    }
}
