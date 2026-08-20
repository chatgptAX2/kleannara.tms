package com.company.module.shipment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * 출고진행현황 행 응답 DTO
 * JS 컬럼 정의(_ssColDefs)는 대문자 key를 사용하므로
 * @JsonProperty 로 모든 필드를 대문자 key로 직렬화한다.
 */
@Getter
@Builder
public class ShipmentRowResponse {

    // ── 출고헤더 (SHPDH) ─────────────────────────────────────
    @JsonProperty("SHPOKY")
    private String shpoky;          // 출고전표 키

    @JsonProperty("WAREKY")
    private String wareky;          // 창고코드

    @JsonProperty("DPTNKY")
    private String dptnky;          // 납품처코드

    /** JS에서 r.DPTNKYNM 으로 접근 */
    @JsonProperty("DPTNKYNM")
    private String dptnm;           // 납품처명

    @JsonProperty("PTRCVRNM")
    private String ptrcvrNm;        // 수취처명

    @JsonProperty("RQSHPD")
    private String rqshpd;          // 납품요청일

    @JsonProperty("DOCDAT")
    private String docdat;          // 문서일자

    @JsonProperty("STATDO")
    private String statdo;          // 출고상태코드

    @JsonProperty("STATDONM")
    private String statdoNm;        // 출고상태명

    @JsonProperty("SHPMTY")
    private String shpmty;          // 출하유형코드

    @JsonProperty("SHPMTYNM")
    private String shpmtyNm;        // 출하유형명

    @JsonProperty("DOCUTYNM")
    private String docutynm;        // 문서유형코드

    @JsonProperty("PRTCHK")
    private String prtchk;          // 출력여부

    @JsonProperty("VEHINO")
    private String vehino;          // 차종코드

    // ── 출고아이템 (SHPDI) ───────────────────────────────────
    @JsonProperty("SHPOIT")
    private String shpoit;          // 출고아이템번호

    @JsonProperty("SKUKEY")
    private String skukey;          // 품목코드

    @JsonProperty("DESC01")
    private String desc01;          // 품목명

    @JsonProperty("SKUG05")
    private String skug05;          // 품목그룹05

    @JsonProperty("SKUG05NM")
    private String skug05Nm;        // 품목그룹05 명

    @JsonProperty("UOMKEY")
    private String uomkey;          // 단위

    @JsonProperty("STATIT")
    private String statit;          // 아이템상태

    @JsonProperty("STDLNR")
    private String stdlnr;          // 가선적번호(배차번호)

    @JsonProperty("SVBELN")
    private String svbeln;          // SAP 납품문서번호

    @JsonProperty("LOTA01")
    private String lota01;          // 로트01(납품일)

    /** JS에서 r.LOTA01NM 으로 접근 — 빈값 고정 */
    @JsonProperty("LOTA01NM")
    @Builder.Default
    private String lota01Nm = "";   // 납품처유형(LOTA01 명) — 현재 미사용, 빈값

    @JsonProperty("LOTA02")
    private String lota02;          // 로트02(플랜트)

    @JsonProperty("LOTA02NM")
    private String lota02Nm;        // 플랜트명

    @JsonProperty("LOTA03")
    private String lota03;          // 로트03(인치)

    @JsonProperty("TLOTA01")
    private String tlota01;         // 목표 로트01

    @JsonProperty("TLOTA02")
    private String tlota02;         // 목표 로트02

    @JsonProperty("TLOTA02NM")
    private String tlota02Nm;       // 목표 플랜트명

    @JsonProperty("MEASKY")
    private String measky;          // 측정단위 키

    @JsonProperty("CREDAT")
    private String credat;          // 생성일자

    @JsonProperty("CRETIM")
    private String cretim;          // 생성시각

    @JsonProperty("CREUSR")
    private String creusr;          // 생성자

    @JsonProperty("LMODAT")
    private String lmodat;          // 수정일자

    @JsonProperty("LMOTIM")
    private String lmotim;          // 수정시각

    @JsonProperty("LMOUSR")
    private String lmousr;          // 수정자

    // ── 수량 (단위별 환산) ───────────────────────────────────
    @JsonProperty("QTSHPO")
    private Double qtshpo;          // 출하수량 (원 단위)

    @JsonProperty("TOT")
    private Double tot;             // 중량 합계 (KG 환산)

    @JsonProperty("BAG")
    private Double bag;             // BAG 환산

    @JsonProperty("BOX")
    private Double box;             // BOX 환산

    @JsonProperty("PLT")
    private Double plt;             // PLT(파렛트) 환산

    @JsonProperty("SOK")
    private Double sok;             // SOK 환산

    @JsonProperty("EA")
    private Double ea;              // EA 환산

    @JsonProperty("BOXBAG")
    private String boxbag;          // BOX/BAG 구분

    @JsonProperty("QTUALO")
    private Double qtualo;          // 납품수량 (QTSHPO - QTALOC)

    @JsonProperty("QTALOC")
    private Double qtaloc;          // 할당수량

    @JsonProperty("QTJCMP")
    private Double qtjcmp;          // 출고완료수량

    @JsonProperty("QTSHPD")
    private Double qtshpd;          // 출고수량

    @JsonProperty("QTUALOBOX")
    private Double qtualoBox;       // 납품수량(BOX환산)

    @JsonProperty("QTALOCBOX")
    private Double qtalocBox;       // 할당수량(BOX환산)

    @JsonProperty("QTJCMPBOX")
    private Double qtjcmpBox;       // 출고완료수량(BOX환산)

    @JsonProperty("QTSHPDBOX")
    private Double qtshpdBox;       // 출고수량(BOX환산)

    // ── PLT개수 (원지/판지) ──────────────────────────────────
    @JsonProperty("PLT_CNT")
    private Integer pltCnt;         // PLT개수 (null=계산불가)

    // ── PLT 수량 (최근 입고 RECDI.QTYRCV = 1PLT당 수량) ──────
    @JsonProperty("PLT_QTY")
    private Double pltQty;          // 1PLT당 수량(원지=Kg/판지=R, null=미등록)

    // ── SOK 환산 (판지 높이 계산용) ──────────────────────────
    @JsonProperty("SOK_PER_R")
    private Double sokPerR;         // 1R당 SOK 수
}
