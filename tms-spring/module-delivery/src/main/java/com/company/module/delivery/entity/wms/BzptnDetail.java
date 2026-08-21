package com.company.module.delivery.entity.wms;

import jakarta.persistence.*;
import lombok.*;

/**
 * 납품처 TMS 상세 (BZPTN_DETAIL) — Oracle WMS DB (wmsPU)
 *
 * ■ DataSource: WmsJpaConfig (Oracle KNRAWMS)
 *   WmsJpaConfig.setPackagesToScan → com.company.module.delivery.entity.wms
 *
 * ※ delivery.entity.wms 서브패키지로 분리한 이유:
 *    delivery.entity (상위) 에 두면 TmsJpaConfig가 delivery.entity.tms 를 스캔할 때
 *    setPackagesToScan 재귀 스캔으로 이 클래스까지 tmsPU에 포함되어 충돌 발생.
 *    → tms/wms 서브패키지를 완전히 분리하여 각 Config가 겹치지 않도록 구조화.
 *
 * 납품처 기본 정보는 BZPTN 테이블 (읽기 전용)
 * Flask: api_delivery_list / api_delivery_save / api_delivery_delete 대응
 */
@Entity
@Table(name = "BZPTN_DETAIL", schema = "KNRAWMS",
       uniqueConstraints = @UniqueConstraint(
           name = "UK_BZPTN_DETAIL",
           columnNames = {"PTNRKY", "PTNRTY", "OWNRKY"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class BzptnDetail {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DETAIL_ID")
    private Long detailId;

    @Column(name = "PTNRKY", length = 20, nullable = false)
    private String ptnrky;          // 납품처코드

    @Column(name = "PTNRTY", length = 5)
    private String ptnrty;          // 파트너 유형 (CT)

    @Column(name = "OWNRKY", length = 10)
    private String ownrky;          // 사업주 (KN)

    @Column(name = "WAREKY", length = 10)
    private String wareky;          // 출하 창고코드

    @Column(name = "ROUTE_CD", length = 20)
    private String routeCd;         // 루트코드

    @Column(name = "ITEM_GROUP", length = 10)
    private String itemGroup;       // 제품군 (10=원지)

    @Column(name = "AREA_CD", length = 20)
    private String areaCd;          // 지역코드

    @Column(name = "UNLOAD_TIME")
    private Integer unloadTime;     // 하차 소요시간(분)

    @Column(name = "MAX_HEIGHT")
    private Double maxHeight;       // 최대 적재 높이(m)

    @Column(name = "AUTO_ALLOC_YN", length = 1)
    private String autoAllocYn;     // 자동배차 여부

    @Column(name = "FORKLIFT_YN", length = 1)
    private String forkliftYn;      // 지게차 여부

    @Column(name = "INB_TIME_FROM1", length = 6)
    private String inbTimeFrom1;    // 입고 시작 시간

    @Column(name = "INB_TIME_TO1", length = 6)
    private String inbTimeTo1;      // 입고 종료 시간

    @Column(name = "MAX_BOX_QTY")
    private Integer maxBoxQty;      // 최대 묶음 수

    @Column(name = "DEADLINE_TIME", length = 6)
    private String deadlineTime;    // 배차 마감 시간

    @Column(name = "MAX_TON")
    private Double maxTon;          // 최대 적재 중량(ton) – 차량제한

    @Column(name = "DYNAMIC_DIST_M")
    private Double dynamicDistM;    // 동적 허용 거리(M) – 납품처별 동적 허용 거리값

    @Column(name = "HANDWORK_YN", length = 1)
    private String handworkYn;

    @Column(name = "AUTO_PLT", length = 10)
    private String autoPlt;

    @Column(name = "SINGLE_ITEM_YN", length = 1)
    private String singleItemYn;

    @Column(name = "NY_TYPE", length = 10)
    private String nyType;

    @Column(name = "SINGLE_HEIGHT")
    private Double singleHeight;

    @Column(name = "DYNAMIC_YN", length = 1)
    private String dynamicYn;

    @Column(name = "LTL_YN", length = 1)
    private String ltlYn;

    @Column(name = "PRIORITY_YN", length = 1)
    private String priorityYn;

    @Column(name = "MIN_QTSIWH")
    private Double minQtsiwh;

    @Column(name = "LATITUDE")
    private Double latitude;

    @Column(name = "LONGITUDE")
    private Double longitude;

    @Column(name = "DEL_YN", length = 1)
    private String delYn;

    @Column(name = "CREDAT", length = 8)
    private String credat;

    @Column(name = "CRETIM", length = 6)
    private String cretim;

    @Column(name = "CREUSR", length = 20)
    private String creusr;

    @Column(name = "LMODAT", length = 8)
    private String lmodat;

    @Column(name = "LMOTIM", length = 6)
    private String lmotim;

    @Column(name = "LMOUSR", length = 20)
    private String lmousr;

    /** 소프트 삭제 */
    public void delete(String lmodat, String lmotim, String lmousr) {
        this.delYn  = "Y";
        this.lmodat = lmodat;
        this.lmotim = lmotim;
        this.lmousr = lmousr;
    }
}
