package com.company.module.vehicle.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * VHCMA 차량 저장/수정 요청 DTO
 */
@Getter
@Setter
public class VhcmaSaveRequest {

    private String  vehicleNo;       // 차량번호 (필수)
    private String  ownrky;          // 사업주 코드 (기본: KN)
    private String  shipPoint;
    private String  productGroup;
    private String  deliveryZone;
    private String  carrier;
    private String  vehicleType;
    private String  vehicleKind;
    private String  vehicleClass;
    private String  cartype;
    private String  carclassCd;
    private String  driverName;
    private String  contactNo;
    private Integer palletQty;
    private String  floorType;
    private String  useYn;
    private String  operableYn;
    private String  fixYn;
    private String  dlvTimeFrom;
    private String  dlvTimeTo;
    private String  vehicleYear;
    private String  deliveryCustomer1;
    private String  deliveryCustomer2;
    private String  delYn;
}
