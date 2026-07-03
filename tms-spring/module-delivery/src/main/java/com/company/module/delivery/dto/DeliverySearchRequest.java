package com.company.module.delivery.dto;

import lombok.Getter;
import lombok.Setter;

/** 납품처 목록 검색 요청 */
@Getter @Setter
public class DeliverySearchRequest {
    private Integer page    = 1;
    private Integer size    = 50;
    private String  vstel;       // 출하지점(WAREKY)
    private String  skug05;      // 제품군(ITEM_GROUP)
    private String  ptnrky;      // 납품처코드/명
    private String  q;           // 전문검색 (코드+명+주소)
    private String  sortCol  = "PTNRKY";
    private String  sortDir  = "ASC";
}
