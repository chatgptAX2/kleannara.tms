package com.company.module.dispatch.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * ⚠️ 프론트엔드는 자동배차 결과(백엔드 AutoDispatchService)/검색결과 row 를
 *    "그대로" 전송한다. 그 결과:
 *      1) items 레벨 키는 DB 컬럼명과 동일한 "대문자"(SHPOKY, SHPOIT ...)
 *      2) vehicle 레벨 일부 키는 snake_case(total_kg 등)
 *      3) 숫자 필드에 Boolean/문자열/빈문자가 섞여 들어올 수 있다.
 *         (예: 검색 row 의 DISPATCHED/IS_SPLIT 는 Boolean, JDBC/JS 계산 결과가
 *          false/"" 로 오염되는 경우)
 *
 *    따라서:
 *      - @JsonAlias 로 대문자·snake_case 키를 모두 수용
 *      - @JsonIgnoreProperties(ignoreUnknown=true) 로 DTO 에 없는 잉여 키 무시
 *      - 숫자 필드는 커스텀 @JsonSetter(Object) 로 Boolean/문자열/숫자를 모두
 *        안전하게 변환 → HttpMessageNotReadableException(400) 방지
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PsDispatchSaveRequest {

    @NotEmpty(message = "vehicles 목록은 필수입니다")
    @Valid
    private List<VehicleBlock> vehicles;

    // ── 공용 숫자 변환 유틸 ─────────────────────────────────────────
    /** Boolean/Number/문자열 등 어떤 JSON 값이 와도 Double 로 안전 변환(변환 불가 시 null) */
    static Double toDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n)  return n.doubleValue();
        if (v instanceof Boolean b) return b ? 1.0 : 0.0;
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        if (s.equalsIgnoreCase("true")  || s.equalsIgnoreCase("Y")) return 1.0;
        if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("N")) return 0.0;
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return null; }
    }

    /** Boolean/Number/문자열 등 어떤 JSON 값이 와도 0/1 Integer 로 정규화(변환 불가 시 null) */
    static Integer toIntFlag(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v instanceof Number n)  return n.intValue() != 0 ? 1 : 0;
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        if (s.equalsIgnoreCase("true")  || s.equalsIgnoreCase("Y")) return 1;
        if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("N")) return 0;
        try { return Integer.parseInt(s) != 0 ? 1 : 0; }
        catch (NumberFormatException e) { return 0; }
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
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

        private Double totalKg;
        @JsonSetter("totalKg")
        @JsonAlias({"total_kg", "TOTAL_KG", "TOTALKG"})
        public void setTotalKg(Object v) { this.totalKg = toDouble(v); }

        @Valid
        @JsonAlias({"ITEMS"})
        private List<ItemBlock> items;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ItemBlock {
        @JsonAlias({"SHPOKY"})
        private String shpoky;
        @JsonAlias({"SHPOIT"})
        private String shpoit;
        @JsonAlias({"SKUKEY"})
        private String skukey;
        @JsonAlias({"DESC01", "SKUNM"})
        private String desc01;
        @JsonAlias({"UOMKEY"})
        private String uomkey;
        @JsonAlias({"DPTNKY"})
        private String dptnky;
        @JsonAlias({"DPTNM"})
        private String dptnm;
        @JsonAlias({"org_shpoky", "ORG_SHPOKY", "ORGSHPOKY"})
        private String orgShpoky;     // 원본 납품문서 (분할 시)
        @JsonAlias({"org_shpoit", "ORG_SHPOIT", "ORGSHPOIT"})
        private String orgShpoit;

        // ── 숫자 필드: Boolean/문자열/숫자 어떤 형태든 안전 변환 ──
        private Double qtshpo;
        @JsonSetter("qtshpo")
        @JsonAlias({"QTSHPO"})
        public void setQtshpo(Object v) { this.qtshpo = toDouble(v); }

        private Double grswgt;
        @JsonSetter("grswgt")
        @JsonAlias({"GRSWGT"})
        public void setGrswgt(Object v) { this.grswgt = toDouble(v); }

        private Double kgWeight;
        @JsonSetter("kgWeight")
        @JsonAlias({"kg_weight", "KG_WEIGHT", "KGWEIGHT"})
        public void setKgWeight(Object v) { this.kgWeight = toDouble(v); }

        private Integer isSplit;      // 분할여부 (0/1)
        @JsonSetter("isSplit")
        @JsonAlias({"is_split", "IS_SPLIT", "ISSPLIT"})
        public void setIsSplit(Object v) { this.isSplit = toIntFlag(v); }
    }
}
