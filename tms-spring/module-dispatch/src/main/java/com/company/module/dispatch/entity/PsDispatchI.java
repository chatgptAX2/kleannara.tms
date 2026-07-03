package com.company.module.dispatch.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * PS 배차 아이템 (PS_DISPATCH_I)
 * 배차헤더(PsDispatchH)와 1:N 관계
 */
@Entity
@Table(name = "ps_dispatch_i",
       uniqueConstraints = @UniqueConstraint(
           name = "UK_PS_DISPATCH_I",
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
    private String dispatchNo;

    @Column(name = "SHPOKY", length = 20, nullable = false)
    private String shpoky;                // 납품문서번호

    @Column(name = "SHPOIT", length = 6, nullable = false)
    private String shpoit;                // 납품문서 라인

    @Column(name = "SKUKEY", length = 30)
    private String skukey;                // 품목코드

    @Column(name = "DESC01", length = 200)
    private String desc01;                // 품목명

    @Column(name = "QTSHPO")
    private Double qtshpo;                // 출하수량

    @Column(name = "UOMKEY", length = 10)
    private String uomkey;                // 단위 (KG, R)

    @Column(name = "KG_WEIGHT")
    private Double kgWeight;              // KG 환산 중량

    @Column(name = "SVBELN", length = 20)
    private String svbeln;                // SAP 납품문서번호

    @Column(name = "DPTNKY", length = 20)
    private String dptnky;

    @Column(name = "RQSHPD", length = 8)
    private String rqshpd;

    /** 수량 분할(split) 시 업데이트 */
    public void updateQty(Double qtshpo, Double kgWeight) {
        this.qtshpo   = qtshpo;
        this.kgWeight = kgWeight;
    }
}
