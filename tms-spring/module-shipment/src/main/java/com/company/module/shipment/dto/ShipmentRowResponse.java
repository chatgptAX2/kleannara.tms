package com.company.module.shipment.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 출고진행현황 행 응답 DTO
 * Flask: /api/shipment/schedule 응답 행 구조와 동일
 */
@Getter
@Builder
public class ShipmentRowResponse {

    // ── 출고헤더 (SHPDH) ─────────────────────────────────────
    private String shpoky;          // 출고전표 키
    private String wareky;          // 창고코드
    private String dptnky;          // 납품처코드
    private String dptnm;           // 납품처명
    private String ptrcvrNm;        // 수취처명
    private String rqshpd;          // 납품요청일
    private String docdat;          // 문서일자
    private String statdo;          // 출고상태코드
    private String statdoNm;        // 출고상태명
    private String shpmty;          // 출하유형코드
    private String shpmtyNm;        // 출하유형명
    private String docutynm;        // 문서유형명
    private String prtchk;          // 출력여부
    private String vehino;          // 차종코드

    // ── 출고아이템 (SHPDI) ───────────────────────────────────
    private String shpoit;          // 출고아이템번호
    private String skukey;          // 품목코드
    private String desc01;          // 품목명
    private String skug05;          // 품목그룹05
    private String skug05Nm;        // 품목그룹05 명
    private String uomkey;          // 단위
    private String statit;          // 아이템상태
    private String stdlnr;          // 가선적번호(배차번호)
    private String svbeln;          // SAP 납품문서번호
    private String lota01;          // 로트01(납품일)
    private String lota02;          // 로트02(플랜트)
    private String lota02Nm;        // 플랜트명
    private String lota03;          // 로트03(인치)
    private String tlota01;         // 목표 로트01
    private String tlota02;         // 목표 로트02
    private String tlota02Nm;       // 목표 플랜트명
    private String measky;          // 측정단위 키
    private String credat;          // 생성일자
    private String cretim;          // 생성시각
    private String creusr;          // 생성자
    private String lmodat;          // 수정일자
    private String lmotim;          // 수정시각
    private String lmousr;          // 수정자

    // ── 수량 (단위별 환산) ───────────────────────────────────
    private Double qtshpo;          // 출하수량 (원 단위)
    private Double tot;             // 중량 합계 (KG 환산)
    private Double bag;             // BAG 환산
    private Double box;             // BOX 환산
    private Double plt;             // PLT(파렛트) 환산
    private Double sok;             // SOK 환산
    private Double ea;              // EA 환산
    private String boxbag;          // BOX/BAG 구분
    private Double qtualo;          // 계획수량
    private Double qtaloc;          // 할당수량
    private Double qtjcmp;          // 출고완료수량
    private Double qtshpd;          // 출고수량
    private Double qtualoBox;       // 계획수량(BOX환산)
    private Double qtalocBox;       // 할당수량(BOX환산)
    private Double qtjcmpBox;       // 출고완료수량(BOX환산)
    private Double qtshpdBox;       // 출고수량(BOX환산)

    // ── PLT개수 (원지/판지) ──────────────────────────────────
    private Integer pltCnt;         // PLT개수 (null=계산불가)

    // ── SOK 환산 (판지 높이 계산용) ──────────────────────────
    private Double sokPerR;         // 1R당 SOK 수
}
