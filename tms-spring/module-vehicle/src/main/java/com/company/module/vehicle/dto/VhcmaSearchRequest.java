package com.company.module.vehicle.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * VHCMA 차량 목록 검색 요청 DTO
 */
@Getter
@Setter
public class VhcmaSearchRequest {
    private Integer page = 1;
    private Integer size = 50;
    private String  shipPoint;
    private String  productGroup;
    private String  deliveryZone;
    private String  carrier;
    private String  vehicleType;
    private String  vehicleKind;
    private String  vehicleClass;
    private String  vehicleNo;
    private String  useYn;        // 사용여부 필터(=삭제여부 기준: Y=사용/미삭제, N=미사용/삭제)
    private String  sortCol;
    private String  sortDir = "ASC";
}
