package com.company.module.delivery.dto;

import lombok.Getter;
import lombok.Setter;

/** 납품처 TMS 상세(BZPTN_DETAIL) 저장 요청 */
@Getter @Setter
public class DeliverySaveRequest {

    private String  ptnrky;
    private String  ptnrty;
    private String  ownrky;
    private String  wareky;
    private String  routeCd;
    private String  itemGroup;
    private Integer unloadTime;
    private String  inbTimeFrom1;
    private String  inbTimeTo1;
    private String  areaCd;
    private Double  maxHeight;
    private String  forkliftYn;
    private String  handworkYn;
    private String  autoPlt;
    private Integer maxBoxQty;
    private String  autoAllocYn;
    private String  singleItemYn;
    private String  nyType;
    private Double  singleHeight;
    private String  dynamicYn;
    private String  ltlYn;
    private String  priorityYn;
    private Double  minQtsiwh;
    private Double  latitude;
    private Double  longitude;
    private String  delYn;
    private String  deadlineTime;
    private Double  maxTon;
    private Double  dynamicDistM;   // 동적 허용 거리(M) — 납품처별 동적 허용 거리값
}
