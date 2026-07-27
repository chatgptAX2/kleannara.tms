package com.company.module.dispatch.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 자동/수기/드래그 배차 저장 요청 DTO
 * Flask: POST /api/ps-dispatch/save  body.vehicles[]
 *
 * ⚠️ 프론트엔드는 자동배차 결과(백엔드 AutoDispatchService)의 원본 키를
 *    그대로 전송한다. items 레벨 키는 DB 컬럼명과 동일한 "대문자"(SHPOKY,
 *    SHPOIT, SKUKEY ...)이며, vehicle 레벨 일부 키는 snake_case(total_kg 등)이다.
 *    Jackson 기본 역직렬화는 대소문자를 구분하므로, @JsonAlias 로 대문자·
 *    snake_case 형태를 모두 수용해야 매핑 누락(→ SHPOKY NULL → ORA-01400)을
 *    방지할 수 있다.
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
        @JsonAlias({"DPTNKY"})
        private String dptnky;
        @JsonAlias({"DPTNM"})
        private String dptnm;
        @JsonAlias({"RQSHPD"})
        private String rqshpd;        // yyyyMMdd 또는 yyyy-MM-dd
        @JsonAlias({"CARTYPE"})
        private String cartype;       // 차종명 (예: 5톤, 18톤)
        @JsonAlias({"carclass_cd", "CARCLASS_CD", "CARCLASSCD"})
        private String carclassCd;    // 차종코드 (예: Z010)
        @JsonAlias({"total_kg", "TOTAL_KG", "TOTALKG"})
        private Double totalKg;
        @Valid
        @JsonAlias({"ITEMS"})
        private List<ItemBlock> items;
    }

    @Getter
    @Setter
    public static class ItemBlock {
        @JsonAlias({"SHPOKY"})
        private String shpoky;
        @JsonAlias({"SHPOIT"})
        private String shpoit;
        @JsonAlias({"SKUKEY"})
        private String skukey;
        @JsonAlias({"DESC01", "SKUNM"})
        private String desc01;
        @JsonAlias({"QTSHPO"})
        private Double qtshpo;
        @JsonAlias({"UOMKEY"})
        private String uomkey;
        @JsonAlias({"DPTNKY"})
        private String dptnky;
        @JsonAlias({"DPTNM"})
        private String dptnm;
        @JsonAlias({"is_split", "IS_SPLIT", "ISSPLIT"})
        private Integer isSplit;      // 분할여부 (0/1)
        @JsonAlias({"org_shpoky", "ORG_SHPOKY", "ORGSHPOKY"})
        private String orgShpoky;     // 원본 납품문서 (분할 시)
        @JsonAlias({"org_shpoit", "ORG_SHPOIT", "ORGSHPOIT"})
        private String orgShpoit;
        @JsonAlias({"GRSWGT"})
        private Double grswgt;
        @JsonAlias({"kg_weight", "KG_WEIGHT", "KGWEIGHT"})
        private Double kgWeight;
    }
}
