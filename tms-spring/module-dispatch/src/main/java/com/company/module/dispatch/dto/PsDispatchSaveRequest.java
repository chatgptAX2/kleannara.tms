package com.company.module.dispatch.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;
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
        private Integer isSplit;      // 분할여부 (0/1)

        /**
         * IS_SPLIT / is_split 역직렬화.
         * 프론트(검색결과 row)는 이 값을 Boolean(true/false) 으로 보내는 반면,
         * 자동배차 결과는 Integer(0/1)/문자열("Y"/"N") 로 보낼 수 있다.
         * 어떤 형태로 와도 0/1 Integer 로 정규화하여 Integer↔Boolean
         * 역직렬화 오류(HttpMessageNotReadableException)를 방지한다.
         */
        @JsonSetter("isSplit")
        @JsonAlias({"is_split", "IS_SPLIT", "ISSPLIT"})
        public void setIsSplit(Object v) {
            this.isSplit = toIntFlag(v);
        }

        private static Integer toIntFlag(Object v) {
            if (v == null) return null;
            if (v instanceof Boolean b)  return b ? 1 : 0;
            if (v instanceof Number n)   return n.intValue() != 0 ? 1 : 0;
            String s = v.toString().trim();
            if (s.isEmpty()) return null;
            if (s.equalsIgnoreCase("true")  || s.equalsIgnoreCase("Y")) return 1;
            if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("N")) return 0;
            try { return Integer.parseInt(s) != 0 ? 1 : 0; }
            catch (NumberFormatException e) { return 0; }
        }
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
