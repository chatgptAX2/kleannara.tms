package com.company.module.vehicle.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * TMS_DS_VEHICLE / CMCDV 저장 요청 DTO
 * Flask: POST /api/carclass/save (table: 'carclass'|'vehicle'|'vehicle_delete')
 */
@Getter
@Setter
public class VehicleSaveRequest {

    /** 저장 대상 테이블: 'carclass' | 'vehicle' | 'vehicle_delete' */
    private String table;

    // ── CMCDV 필드 (table='carclass') ──
    private String cmcdvl;    // 차량유형코드 (PK)
    private String cdesc1;    // 차량톤수명
    private String usarg1;
    private String usarg2;
    private String usarg3;
    private String usarg4;
    private String usarg5;
    // 고정차량 사양(톤수별) 전용: 길이/너비/높이 (USARG6/7/8)
    private String usarg6;   // 길이(m)
    private String usarg7;   // 너비(m)
    private String usarg8;   // 높이(m)

    // ── TMS_DS_VEHICLE 필드 (table='vehicle') ──
    private String  carclassCd;
    private String  cartype;
    private Double  lengthM;
    private String  widthM;
    private Double  heightM;
    private Double  loadTon;
    private Integer sortSeq;
    private Double  palletHeightM;
    private Integer palletCnt;
    private String  longAxisYn;
    private Integer inch12Lt300;
    private Integer inch12Ge300;
    private Integer inch3Lt300;
    private Integer inch3Ge300;
    private Integer defaultVehCnt;
}
