package com.company.module.shipment.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 출고 아이템 (SHPDI)
 * - 출고전표 라인 정보 (품목, 수량, 배차번호, 로트 등)
 * - SAP 연계 테이블: ddl-auto=none, 조회 전용
 */
@Entity
@Table(name = "SHPDI")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(ShpdIId.class)
public class ShpdI {

    @Id
    @Column(name = "SHPOKY", length = 20, nullable = false)
    private String shpoky;                  // 출고전표 키 (PK1)

    @Id
    @Column(name = "SHPOIT", length = 6, nullable = false)
    private String shpoit;                  // 출고아이템번호 (PK2)

    @Column(name = "SKUKEY", length = 30)
    private String skukey;                  // 품목코드

    @Column(name = "DESC01", length = 200)
    private String desc01;                  // 품목명

    @Column(name = "SKUG05", length = 10)
    private String skug05;                  // 품목그룹05 (10=원지/판지)

    @Column(name = "MEASKY", length = 30)
    private String measky;                  // 측정단위 키

    @Column(name = "UOMKEY", length = 10)
    private String uomkey;                  // 단위 (KG, R, EA 등)

    @Column(name = "QTSHPO")
    private Double qtshpo;                  // 출하수량

    @Column(name = "QTUALO")
    private Double qtualo;                  // 계획수량

    @Column(name = "QTALOC")
    private Double qtaloc;                  // 할당수량

    @Column(name = "QTJCMP")
    private Double qtjcmp;                  // 출고완료수량

    @Column(name = "QTSHPD")
    private Double qtshpd;                  // 출고수량

    @Column(name = "STATIT", length = 10)
    private String statit;                  // 아이템 상태 (NEW/DONE 등)

    @Column(name = "STDLNR", length = 20)
    private String stdlnr;                  // 가선적번호 (배차번호)

    @Column(name = "SVBELN", length = 20)
    private String svbeln;                  // SAP 납품문서번호

    @Column(name = "LOTA01", length = 20)
    private String lota01;                  // 로트 01 (납품일자)

    @Column(name = "LOTA02", length = 20)
    private String lota02;                  // 로트 02 (플랜트)

    @Column(name = "LOTA03", length = 20)
    private String lota03;                  // 로트 03 (인치)

    @Column(name = "TLOTA01", length = 20)
    private String tlota01;                 // 목표 로트 01

    @Column(name = "TLOTA02", length = 20)
    private String tlota02;                 // 목표 로트 02 (플랜트)

    @Column(name = "ALSTKY", length = 20)
    private String alstky;                  // 배치 키

    @Column(name = "CREDAT", length = 8)
    private String credat;                  // 생성일자

    @Column(name = "CRETIM", length = 6)
    private String cretim;                  // 생성시각

    @Column(name = "CREUSR", length = 20)
    private String creusr;                  // 생성자

    @Column(name = "LMODAT", length = 8)
    private String lmodat;                  // 수정일자

    @Column(name = "LMOTIM", length = 6)
    private String lmotim;                  // 수정시각

    @Column(name = "LMOUSR", length = 20)
    private String lmousr;                  // 수정자
}
