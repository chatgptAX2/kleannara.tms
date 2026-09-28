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
    private String svbeln;          // SAP 납품문서번호 (SAP 오더번호)

    @JsonProperty("SPOSNR")
    private String sposnr;          // SAP 오더 아이템번호

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

    // ── [3D 일치] 적재뷰(3D) 계산 필드 — 배차확정 탭 sapItems 와 '동일 산식' ──
    //   배차저장 前(자동배차 items = searchDocs 결과)과 저장 後(sapItems)의 3D 입력을
    //   맞춰 원지/판지 배치가 일치하도록 노출. 프론트 _lvComputePlacement 가 판지 단수/
    //   높이 산출에 사용하는 대문자 JSON 키(SOK_PER_R / PLT_PER_UNIT / THICKNESS).
    @JsonProperty("SOK_PER_R")
    private Double sokPerR;         // 1R당 SOK 환산계수 (판지 속단위, MEASI)

    @JsonProperty("PLT_PER_UNIT")
    private Double pltPerUnit;      // 판지(SKUG05='10') PLT당 개수 (RECDI FRV, 단수 산출)

    @JsonProperty("THICKNESS")
    private Double thickness;       // TMS_THICKNESS(µm) — 판지 1단(1PLT) 높이 산출

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
    private Boolean dispatched;     // 배차완료 여부 (TMS_SHPDI.STDLNR 채번 여부)

    @JsonProperty("IS_SAVED")
    private Boolean isSaved;        // 배차저장 완료 여부 (STDLNR 채번 = DB 반영됨)

    @JsonProperty("STDLNR")
    private String stdlnr;          // 가선적번호 (TMS_SHPDI.STDLNR)

    @JsonProperty("STKNUM")
    private String stknum;          // SAP 선적번호 (TMS_SHPDI.STKNUM, 선적생성 완료 시)

    @JsonProperty("IS_SPLIT")
    private Boolean isSplit;        // 분할문서 여부 (SHPOKY '-S' 패턴)

    // 연동구분 (TMS_SHPDI.DESC02): 'OFFLINE'=미연동(테스트), 'ONLINE'=연동, 그 외/공백=기존
    //   프론트 호환 위해 JSON 키는 TMS_LINK_YN 유지.
    @JsonProperty("TMS_LINK_YN")
    private String linkYn;
}
