package com.company.module.delivery.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

/** 납품처 목록 검색 요청 */
@Getter @Setter
public class DeliverySearchRequest {
    private Integer page    = 1;
    private Integer size    = 50;
    private String  vstel;       // 출하지점(WAREKY) — 단일값 (출하지점 select)
    private List<String> werks;  // 플랜트 multi-select → WAREKY IN 조건
    private String  skug05;      // 제품군(ITEM_GROUP)
    private String  ptnrky;      // 납품처코드/명
    private String  q;           // 전문검색 (코드+명+주소)
    private String  sortCol  = "PTNRKY";
    private String  sortDir  = "ASC";
}
