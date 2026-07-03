package com.company.module.delivery.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 경로별 운송비 마스터 (ROUTE_COST)
 * Flask: api_route_cost_search / api_route_cost_pivot 대응
 */
@Entity
@Table(name = "route_cost")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class RouteCost {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COST_ID")
    private Long costId;

    @Column(name = "WAREKY", length = 10)
    private String wareky;        // 출하 창고코드

    @Column(name = "PTNRKY", length = 20)
    private String ptnrky;        // 납품처코드

    @Column(name = "CARTYPE", length = 50)
    private String cartype;       // 차종명

    @Column(name = "COST_AMT")
    private Double costAmt;       // 운송비(원)

    @Column(name = "DIST_KM")
    private Double distKm;        // 거리(km)

    @Column(name = "EFF_DATE", length = 8)
    private String effDate;       // 적용일자 (yyyyMMdd)

    @Column(name = "EXP_DATE", length = 8)
    private String expDate;       // 종료일자

    @Column(name = "UPDDAT", length = 8)
    private String upddat;

    @Column(name = "UPDUSR", length = 20)
    private String updusr;

    public void update(Double costAmt, Double distKm, String expDate, String upddat, String updusr) {
        this.costAmt = costAmt;
        this.distKm  = distKm;
        this.expDate = expDate;
        this.upddat  = upddat;
        this.updusr  = updusr;
    }
}
