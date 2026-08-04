package com.company.module.dispatch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * 반품 배차 대상 납품문서 단건 응답 DTO
 * 원천: KNRAWMS.IFWMS103 (반품입고 BWART=131) + SKUMA + BZPTN
 *
 * 프론트(반품 배차 탭 / 반품출고 메뉴) 렌더러는 UPPER_CASE key 를 사용하므로
 * @JsonProperty 로 모든 필드를 UPPER_CASE 로 직렬화한다.
 * 기존 PS배차 로직/DTO(PsDispatchDocResponse)는 건드리지 않는 신규 DTO.
 */
@Getter
@Builder
public class PsReturnDocResponse {

    // ── 문서 키 ────────────────────────────────────────────────
    @JsonProperty("SEQNO")
    private String seqno;           // IFWMS103 고유값

    @JsonProperty("EBELN")
    private String ebeln;           // 납품문서번호

    @JsonProperty("EBELP")
    private String ebelp;           // 납품문서번호 아이템

    // ── 거점 / 유형 ────────────────────────────────────────────
    @JsonProperty("WAREKY")
    private String wareky;          // 거점코드

    @JsonProperty("BWART")
    private String bwart;           // WMS 문서유형 (131:반품입고)

    @JsonProperty("EINDT")
    private String eindt;           // 입고예정일 (yyyyMMdd)

    // ── 납품처 ─────────────────────────────────────────────────
    @JsonProperty("LIFNR")
    private String lifnr;           // 납품처코드

    @JsonProperty("DPTNNM")
    private String dptnnm;          // 납품처명 (BZPTN.NAME01)

    @JsonProperty("PTNRTY")
    private String ptnrty;          // 납품처유형 (CT:납품처, VD:공급처)

    @JsonProperty("BEDAT")
    private String bedat;           // 전기일자

    @JsonProperty("IFID")
    private String ifid;            // IF 코드

    @JsonProperty("BWARTSAP")
    private String bwartsap;        // SAP 문서유형

    @JsonProperty("DLEFLG")
    private String dleflg;          // 삭제여부

    // ── 품목 ───────────────────────────────────────────────────
    @JsonProperty("SKUKEY")
    private String skukey;          // 품목코드

    @JsonProperty("DESC01")
    private String desc01;          // 품목명

    @JsonProperty("SKUG05")
    private String skug05;          // 제품군 (10:제지, 20:생활)

    @JsonProperty("MEINS")
    private String meins;           // 단위

    // ── 위치 / 배치 ────────────────────────────────────────────
    @JsonProperty("LOTA01")
    private String lota01;          // 저장위치

    @JsonProperty("LOTA02")
    private String lota02;          // 플랜트

    @JsonProperty("LOTA14")
    private String lota14;          // 개별바코드

    @JsonProperty("LOTA15")
    private String lota15;          // 배치번호

    @JsonProperty("FLOTA01")
    private String flota01;         // 저장위치(FROM)

    @JsonProperty("FLOTA02")
    private String flota02;         // 플랜트(FROM)

    // ── 선적번호(배차상태 판단용) ──────────────────────────────
    @JsonProperty("STKNUM")
    private String stknum;          // 선적번호 (가선적번호). ' '=미배차, 값존재=배차완료

    // ── 수량 환산 ──────────────────────────────────────────────
    @JsonProperty("TOT")
    private String tot;             // 톤(KG 환산)

    @JsonProperty("BAG")
    private String bag;             // BAG

    @JsonProperty("BOX")
    private String box;             // BOX

    @JsonProperty("PLT")
    private String plt;             // PLT(PAL)

    @JsonProperty("SOK")
    private String sok;             // SOK

    @JsonProperty("EA")
    private String ea;              // EA

    @JsonProperty("BOXBAG")
    private String boxbag;          // BOX/BAG

    // ── 배차상태 (파생) ────────────────────────────────────────
    //  IFWMS103.STKNUM != ' ' 이면서 값이 존재 → 배차완료(true), 그 외 → 미완료(false)
    @JsonProperty("DISPATCHED")
    private Boolean dispatched;
}
