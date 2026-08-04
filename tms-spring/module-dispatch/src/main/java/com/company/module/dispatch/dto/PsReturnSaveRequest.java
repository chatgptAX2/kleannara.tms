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
 * 반품 배차 저장 요청 DTO (신규)
 * POST /api/ps-return/save  body.vehicles[]
 *
 * ※ 기존 PsDispatchSaveRequest(판매/이송 배차)는 건드리지 않는 신규 DTO.
 *   반품 대상은 KNRAWMS.IFWMS103 → 문서 키가 EBELN(납품문서번호)/EBELP(아이템).
 *   저장 시:
 *     - PS_DISPATCH_H INSERT (DISPATCH_TYPE='GR')
 *     - PS_DISPATCH_D INSERT (SHPOKY=EBELN, SHPOIT=EBELP 로 매핑하여 저장 — 호환)
 *     - UPDATE IFWMS103 SET STKNUM=가선적번호(DISPATCH_NO)
 *
 * 프론트가 검색 row 를 그대로 전송하므로 대문자/snake_case 키를 폭넓게 수용한다.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PsReturnSaveRequest {

    @NotEmpty(message = "vehicles 목록은 필수입니다")
    @Valid
    private List<VehicleBlock> vehicles;

    // ── 공용 숫자 변환 유틸 ─────────────────────────────────────────
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
        @JsonAlias({"LIFNR"})
        private String lifnr;         // 납품처코드 (배차 대표 납품처)
        @JsonAlias({"DPTNNM", "DPTNM"})
        private String dptnnm;        // 납품처명
        @JsonAlias({"RQSHPD", "EINDT"})
        private String rqshpd;        // 배차예정일 (yyyyMMdd 또는 yyyy-MM-dd) — 입고예정일 기준
        @JsonAlias({"WAREKY"})
        private String wareky;        // 거점
        @JsonAlias({"CARTYPE"})
        private String cartype;       // 차종명
        @JsonAlias({"carclass_cd", "CARCLASS_CD", "CARCLASSCD"})
        private String carclassCd;    // 차종코드

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
        @JsonAlias({"EBELN"})
        private String ebeln;         // 납품문서번호 (= SHPOKY 로 저장)
        @JsonAlias({"EBELP"})
        private String ebelp;         // 납품문서 아이템 (= SHPOIT 로 저장)
        @JsonAlias({"SKUKEY"})
        private String skukey;
        @JsonAlias({"DESC01", "SKUNM"})
        private String desc01;
        @JsonAlias({"MEINS", "UOMKEY"})
        private String uomkey;
        @JsonAlias({"LIFNR", "DPTNKY"})
        private String dptnky;        // 납품처코드
        @JsonAlias({"DPTNNM", "DPTNM"})
        private String dptnm;         // 납품처명

        private Double qtshpo;
        @JsonSetter("qtshpo")
        @JsonAlias({"QTSHPO", "TOT"})
        public void setQtshpo(Object v) { this.qtshpo = toDouble(v); }

        private Double grswgt;
        @JsonSetter("grswgt")
        @JsonAlias({"GRSWGT"})
        public void setGrswgt(Object v) { this.grswgt = toDouble(v); }

        private Double kgWeight;
        @JsonSetter("kgWeight")
        @JsonAlias({"kg_weight", "KG_WEIGHT", "KGWEIGHT"})
        public void setKgWeight(Object v) { this.kgWeight = toDouble(v); }
    }
}
