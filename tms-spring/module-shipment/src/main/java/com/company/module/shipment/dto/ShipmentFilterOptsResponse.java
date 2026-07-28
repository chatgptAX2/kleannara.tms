package com.company.module.shipment.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 출고진행현황 필터 옵션 응답 DTO
 * Flask: /api/shipment/schedule/filter-opts 응답 구조와 동일
 */
@Getter
@Builder
public class ShipmentFilterOptsResponse {

    private List<CodeLabel> warekyList;             // 창고 목록 (WAHMA: WAREKY→value, NAME01→label)
    private List<CodeLabel> statitList;             // 출고상태 목록
    private List<CodeLabel> skug05List;             // 품목그룹05 목록 (CMCDV SKUG05)
    private List<CodeLabel> lota02List;             // 플랜트(LOTA02) 목록 (CMCDV LOTA02: CMCDVL→value, CDESC1→label)

    @Getter
    @Builder
    public static class CodeLabel {
        private String value;                       // 코드값
        private String label;                       // 코드명
    }
}
