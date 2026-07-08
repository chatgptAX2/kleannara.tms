package com.company.module.shipment.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 출고 헤더 (SHPDH)
 * - 출고전표 헤더 정보 (납품처, 창고, 출고상태, 출하유형 등)
 * - SAP 연계 테이블: ddl-auto=none, INSERT/UPDATE 금지 (조회 전용)
 */
@Entity
@Table(name = "SHPDH")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShpdH {

    @Id
    @Column(name = "SHPOKY", length = 20, nullable = false)
    private String shpoky;                  // 출고전표 키 (PK)

    @Column(name = "WAREKY", length = 10)
    private String wareky;                  // 창고코드

    @Column(name = "OWNRKY", length = 10)
    private String ownrky;                  // 회사코드

    @Column(name = "DPTNKY", length = 20)
    private String dptnky;                  // 납품처코드

    @Column(name = "PTRCVR", length = 20)
    private String ptrcvr;                  // 수취처코드

    @Column(name = "RQSHPD", length = 8)
    private String rqshpd;                  // 납품요청일 (yyyyMMdd)

    @Column(name = "DOCDAT", length = 8)
    private String docdat;                  // 문서일자 (yyyyMMdd)

    @Column(name = "STATDO", length = 10)
    private String statdo;                  // 출고상태코드

    @Column(name = "SHPMTY", length = 10)
    private String shpmty;                  // 출하유형코드

    @Column(name = "DOCUTY", length = 10)
    private String docuty;                  // 문서유형

    @Column(name = "PRTCHK", length = 1)
    private String prtchk;                  // 출력여부 (Y/N)

    @Column(name = "VEHINO", length = 20)
    private String vehino;                  // 차종코드

    @Column(name = "CARTON", length = 20)
    private String carton;                  // 차번호

    @Column(name = "CARNO", length = 20)
    private String carno;                   // 차량번호

    @Column(name = "DRIVER", length = 50)
    private String driver;                  // 운전기사명

    @Column(name = "DRIVERCEL", length = 20)
    private String drivercel;              // 운전기사 연락처

    @Column(name = "CREDAT", length = 8)
    private String credat;                  // 생성일자

    @Column(name = "CREUSR", length = 20)
    private String creusr;                  // 생성자

    @Column(name = "LMODAT", length = 8)
    private String lmodat;                  // 수정일자

    @Column(name = "LMOUSR", length = 20)
    private String lmousr;                  // 수정자
}
