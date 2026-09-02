package com.company.module.vehicle.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * VHCMA 차량 저장/수정 요청 DTO
 *
 * ※ 프론트(vhcSave)는 JSON 키를 UPPER_SNAKE_CASE(VEHICLE_NO, SHIP_POINT …)로 전송한다.
 *    Jackson 기본 매핑은 camelCase(vehicleNo) 기준이라 그대로면 값이 바인딩되지 않아
 *    vehicleNo == null → "차량번호(VEHICLE_NO)는 필수입니다" 오류가 발생.
 *    → 각 필드에 @JsonProperty 로 실제 전송 키를 명시 매핑한다.
 */
@Getter
@Setter
public class VhcmaSaveRequest {

    // VEHICLE_ID : 시퀀스(VHCMA_SEQ) 자동채번 PK. 프론트에서 참조용으로 전송(신규=빈값, 수정=기존 ID).
    //   INSERT는 시퀀스, UPDATE는 VEHICLE_NO+OWNRKY 기준이므로 키 변경에는 사용하지 않는다.
    @JsonProperty("VEHICLE_ID")          private String  vehicleId;       // 차량ID(읽기전용/참조용)
    @JsonProperty("VEHICLE_NO")          private String  vehicleNo;       // 차량번호 (필수)
    @JsonProperty("OWNRKY")              private String  ownrky;          // 사업주 코드 (기본: KN)
    @JsonProperty("SHIP_POINT")          private String  shipPoint;
    @JsonProperty("PRODUCT_GROUP")       private String  productGroup;
    @JsonProperty("DELIVERY_ZONE")       private String  deliveryZone;
    @JsonProperty("CARRIER")             private String  carrier;
    @JsonProperty("VEHICLE_TYPE")        private String  vehicleType;
    @JsonProperty("VEHICLE_KIND")        private String  vehicleKind;
    @JsonProperty("VEHICLE_CLASS")       private String  vehicleClass;
    @JsonProperty("CARTYPE")             private String  cartype;
    @JsonProperty("CARCLASS_CD")         private String  carclassCd;
    @JsonProperty("DRIVER_NAME")         private String  driverName;
    @JsonProperty("CONTACT_NO")          private String  contactNo;
    @JsonProperty("PALLET_QTY")          private Integer palletQty;
    @JsonProperty("FLOOR_TYPE")          private String  floorType;
    @JsonProperty("USE_YN")              private String  useYn;
    @JsonProperty("OPERABLE_YN")         private String  operableYn;
    @JsonProperty("FIX_YN")              private String  fixYn;
    @JsonProperty("DLV_TIME_FROM")       private String  dlvTimeFrom;
    @JsonProperty("DLV_TIME_TO")         private String  dlvTimeTo;
    @JsonProperty("VEHICLE_YEAR")        private String  vehicleYear;
    @JsonProperty("DELIVERY_CUSTOMER_1") private String  deliveryCustomer1;
    @JsonProperty("DELIVERY_CUSTOMER_2") private String  deliveryCustomer2;
    @JsonProperty("DEL_YN")              private String  delYn;
}
