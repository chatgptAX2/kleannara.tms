package com.company.module.dispatch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * PS 배차 납품문서 단건 응답 DTO
 * Flask: api_ps_dispatch_search() 결과 row 대응
 *
 * JS 렌더러(psdRenderDocTable)는 UPPER_CASE key를 사용하므로
 * @JsonProperty 로 모든 필드를 UPPER_CASE로 직렬화한다.
 */
@Getter
@Builder
public class PsDispatchDocResponse {

    // ── 출고문서 키 ────────────────────────────────────────────
    @JsonProperty("SHPOKY")
    private String shpoky;          // 납품문서번호

    @JsonProperty("SHPOIT")
    private String shpoit;          // 납품문서 아이템번호

    @JsonProperty("SVBELN")
    private String svbeln;          // SAP 납품문서번호

    // ── 품목 ───────────────────────────────────────────────────
    @JsonProperty("SKUKEY")
    private String skukey;          // SKU 키

    @JsonProperty("DESC01")
    private String desc01;          // 품목명

    @JsonProperty("UOMKEY")
    private String uomkey;          // 단위 (KG / R)

    @JsonProperty("SKUG05")
    private String skug05;          // 제품군 코드

    @JsonProperty("SKU_TYPE")
    private String skuType;         // roll | board | other

    @JsonProperty("INCH")
    private String inch;            // 인치 (12인치 / 3인치)

    @JsonProperty("GRM_COND")
    private String grmCond;         // 평량 구분 (GE300 / LT300)

    @JsonProperty("LOTA03")
    private String lota03;          // 포장타입

    // ── 수량 / 중량 / CBM ──────────────────────────────────────
    @JsonProperty("QTSHPO")
    private Double qtshpo;          // 납품수량

    @JsonProperty("GRSWGT")
    private Double grswgt;          // 총중량 (SKUMA)

    @JsonProperty("KG_WEIGHT")
    private Double kgWeight;        // 환산 KG 중량

    @JsonProperty("UNIT_WEIGHT")
    private Double unitWeight;      // RECDI 기반 단일 롤 중량 (0=미등록)

    @JsonProperty("ROLL_COUNT")
    private Integer rollCount;      // 원지 롤 수

    @JsonProperty("ROLL_CBM")
    private Double rollCbm;         // 원지 CBM

    @JsonProperty("BOARD_CBM")
    private Double boardCbm;        // 판지 CBM

    // ── 납품처 ─────────────────────────────────────────────────
    @JsonProperty("DPTNKY")
    private String dptnky;          // 납품처 코드

    @JsonProperty("DPTNM")
    private String dptnm;           // 납품처명

    // ── 일자 / 출하유형 ────────────────────────────────────────
    @JsonProperty("DOCDAT")
    private String docdat;          // 문서일자

    @JsonProperty("RQSHPD")
    private String rqshpd;          // 납품요청일 (yyyyMMdd)

    @JsonProperty("SHPMTY")
    private String shpmty;          // 출하유형 코드

    @JsonProperty("SHPMTY_NM")
    private String shpmtyNm;        // 출하유형명

    // ── 배차 상태 ──────────────────────────────────────────────
    @JsonProperty("DISPATCHED")
    private Boolean dispatched;     // 배차완료 여부 (SHPDI.STDLNR 채번 여부)

    /** IS_SAVED, STDLNR: Service 레이어에서 추가로 설정하지 않으면 null/false */
    @JsonProperty("IS_SPLIT")
    private Boolean isSplit;        // 분할문서 여부 (SHPOKY '-S' 패턴)
}
