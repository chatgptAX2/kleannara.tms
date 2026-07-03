package com.company.module.vehicle.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * DS_VEHICLE 차량제원 응답 DTO
 * Flask: api_ds_vehicle() 결과 대응
 */
@Getter
@Builder
public class DsVehicleResponse {
    private String carclassCd;
    private String cartype;
    private Double lengthM;
    private String widthM;
    private Double widthMNum;    // WIDTH_M 범위값 최솟값 (숫자 계산용)
    private Double heightM;
    private Double loadTon;
    private Double loadKg;       // loadTon × 1000
    private Integer sortSeq;
    private Double palletHeightM;
    private Integer palletCnt;
    private String longAxisYn;
    private Integer inch12Lt300;
    private Integer inch12Ge300;
    private Integer inch3Lt300;
    private Integer inch3Ge300;
    private Integer defaultVehCnt;
    private String upddat;
    private String updusr;
}
