package com.company.module.dispatch.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * PS 배차 아이템 (ps_dispatch_d)
 * - DB 테이블명: ps_dispatch_d  (DDL 01_schema.sql 기준)
 * - Entity 클래스명: PsDispatchI  (기존 코드 호환 유지)
 * 배차헤더(PsDispatchH)와 1:N 관계
 */
@Entity
@Table(name = "PS_DISPATCH_D",
       uniqueConstraints = @UniqueConstraint(
           name = "UK_PS_DISPATCH_D",
           columnNames = {"DISPATCH_NO", "SHPOKY", "SHPOIT"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class PsDispatchI {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ITEM_ID")
    private Long itemId;

    @Column(name = "DISPATCH_NO", length = 20, nullable = false)
    private String dispatchNo;              // 배차번호 (FK → ps_dispatch_h)

    @Column(name = "SEQ")
    private Integer seq;                    // 순번

    @Column(name = "SHPOKY", length = 20, nullable = false)
    private String shpoky;                  // 납품문서번호

    @Column(name = "SHPOIT", length = 6, nullable = false)
    private String shpoit;                  // 납품문서 라인

    @Column(name = "SKUKEY", length = 30)
    private String skukey;                  // 품목코드

    @Column(name = "DESC01", length = 200)
    private String desc01;                  // 품목명

    @Column(name = "QTSHPO")
    private Double qtshpo;                  // 출하수량

    @Column(name = "UOMKEY", length = 10)
    private String uomkey;                  // 단위 (KG, R)

    @Column(name = "DPTNKY", length = 20)
    private String dptnky;                  // 납품처코드

    @Column(name = "DPTNM", length = 100)
    private String dptnm;                   // 납품처명

    @Column(name = "IS_SPLIT")
    private Integer isSplit;                // 분할 여부 (0/1)

    @Column(name = "ORG_SHPOKY", length = 20)
    private String orgShpoky;              // 원본 납품문서번호 (분할 시)

    @Column(name = "ORG_SHPOIT", length = 6)
    private String orgShpoit;              // 원본 납품문서 라인 (분할 시)

    @Column(name = "GRSWGT")
    private Double grswgt;                  // 묶음당 중량(kg)

    @Column(name = "KG_WEIGHT")
    private Double kgWeight;               // KG 환산 중량

    @Column(name = "SVBELN", length = 20)
    private String svbeln;                  // SAP 납품문서번호

    @Column(name = "RQSHPD", length = 8)
    private String rqshpd;                  // 납품요청일

    /** 수량 분할(split) 시 업데이트 */
    public void updateQty(Double qtshpo, Double kgWeight) {
        this.qtshpo   = qtshpo;
        this.kgWeight = kgWeight;
    }
}
