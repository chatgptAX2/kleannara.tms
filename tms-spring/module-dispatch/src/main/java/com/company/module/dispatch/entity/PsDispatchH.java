package com.company.module.dispatch.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * PS 배차 헤더 (PS_DISPATCH_H)
 * Flask: PS_DISPATCH_H 테이블 대응
 */
@Entity
@Table(name = "ps_dispatch_h")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class PsDispatchH {

    @Id
    @Column(name = "DISPATCH_NO", length = 20)
    private String dispatchNo;             // 배차번호 (예: 260509001T)

    @Column(name = "RQSHPD", length = 8)
    private String rqshpd;                // 납품요청일 (YYYYMMDD)

    @Column(name = "DPTNKY", length = 20)
    private String dptnky;                // 납품처코드

    @Column(name = "DPTNM", length = 100)
    private String dptnm;                 // 납품처명

    @Column(name = "CARTYPE", length = 20)
    private String cartype;               // 차종 (예: 5톤, 18톤)

    @Column(name = "CARCLASS_CD", length = 20)
    private String carclassCd;            // 차종코드

    @Column(name = "TOTAL_KG")
    private Double totalKg;               // 총 중량(KG)

    @Column(name = "TOTAL_CNT")
    private Integer totalCnt;             // 총 건수

    @Column(name = "LOAD_KG")
    private Double loadKg;                // 차량 적재가능 중량

    @Column(name = "MATERIAL_TYPE", length = 10)
    private String materialType;          // ROLL / BOARD / OTHER

    @Column(name = "STAT_CD", length = 10)
    private String statCd;                // PENDING / CONFIRMED / CANCELLED

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.statCd == null) this.statCd = "PENDING";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** 배차 확정 */
    public void confirm() {
        this.statCd = "CONFIRMED";
    }

    /** 배차 취소 */
    public void cancel() {
        this.statCd = "CANCELLED";
    }

    /** 차종 변경 */
    public void updateCartype(String cartype, String carclassCd, Double loadKg) {
        this.cartype    = cartype;
        this.carclassCd = carclassCd;
        this.loadKg     = loadKg;
    }
}
