package com.company.module.dispatch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 자동/수기/드래그 배차 저장 요청 DTO
 * Flask: POST /api/ps-dispatch/save  body.vehicles[]
 */
@Getter
@Setter
public class PsDispatchSaveRequest {

    @NotEmpty(message = "vehicles 목록은 필수입니다")
    @Valid
    private List<VehicleBlock> vehicles;

    @Getter
    @Setter
    public static class VehicleBlock {
        private String dptnky;
        private String dptnm;
        private String rqshpd;        // yyyyMMdd 또는 yyyy-MM-dd
        private String cartype;       // 차종명 (예: 5톤, 18톤)
        private String carclassCd;    // 차종코드 (예: Z010)
        private Double totalKg;
        @Valid
        private List<ItemBlock> items;
    }

    @Getter
    @Setter
    public static class ItemBlock {
        private String shpoky;
        private String shpoit;
        private String skukey;
        private String desc01;
        private Double qtshpo;
        private String uomkey;
        private String dptnky;
        private String dptnm;
        private Integer isSplit;      // 분할여부 (0/1)
        private String orgShpoky;     // 원본 납품문서 (분할 시)
        private String orgShpoit;
        private Double grswgt;
        private Double kgWeight;
    }
}
