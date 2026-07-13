package com.company.module.vehicle.entity.tms;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 차량 제원 마스터 (DS_VEHICLE) — MariaDB (tmsPU)
 *
 * ■ DataSource: TmsJpaConfig (MariaDB integration DB)
 *   TmsJpaConfig.setPackagesToScan → com.company.module.vehicle.entity.tms
 *
 * ※ vehicle.entity.tms 서브패키지로 분리한 이유:
 *    vehicle.entity (상위) 에 두면 WmsJpaConfig가 vehicle.entity.wms 를 스캔할 때
 *    setPackagesToScan 재귀 스캔으로 이 클래스까지 wmsPU에 포함되어 충돌 발생.
 *    → tms/wms 서브패키지를 완전히 분리하여 각 Config가 겹치지 않도록 구조화.
 */
@Entity
@Table(name = "DS_VEHICLE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class DsVehicle {

    @Id
    @Column(name = "CARCLASS_CD", length = 20)
    private String carclassCd;       // 차종코드 (PK, 예: Z010)

    @Column(name = "CARTYPE", length = 50)
    private String cartype;           // 차종명 (예: 1톤)

    @Column(name = "LENGTH_M")
    private Double lengthM;           // 차량 길이(m)

    @Column(name = "WIDTH_M", length = 20)
    private String widthM;            // 차량 너비(m) – 범위 가능 (예: 1.8~2.1)

    @Column(name = "HEIGHT_M")
    private Double heightM;           // 차량 높이(m)

    @Column(name = "LOAD_TON")
    private Double loadTon;           // 적재가능 중량(ton)

    @Column(name = "SORT_SEQ")
    private Integer sortSeq;          // 정렬순서

    @Column(name = "PALLET_HEIGHT_M")
    private Double palletHeightM;     // 팔레트 높이(m)

    @Column(name = "PALLET_CNT")
    private Integer palletCnt;        // 팔레트 수

    @Column(name = "LONG_AXIS_YN", length = 1)
    private String longAxisYn;        // 장축 여부 (Y/N)

    @Column(name = "INCH12_LT300")
    private Integer inch12Lt300;      // 12인치 LT300 최대 롤 수

    @Column(name = "INCH12_GE300")
    private Integer inch12Ge300;      // 12인치 GE300 최대 롤 수

    @Column(name = "INCH3_LT300")
    private Integer inch3Lt300;       // 3인치 LT300 최대 롤 수

    @Column(name = "INCH3_GE300")
    private Integer inch3Ge300;       // 3인치 GE300 최대 롤 수

    @Column(name = "DEFAULT_VEH_CNT")
    private Integer defaultVehCnt;    // 기본 배차 대수

    @Column(name = "UPDDAT", length = 8)
    private String upddat;

    @Column(name = "UPDUSR", length = 20)
    private String updusr;

    /** 제원 업데이트 (비즈니스 메서드) */
    public void update(String cartype, Double lengthM, String widthM, Double heightM,
                       Double loadTon, Integer sortSeq, Double palletHeightM,
                       Integer palletCnt, String longAxisYn,
                       Integer inch12Lt300, Integer inch12Ge300,
                       Integer inch3Lt300, Integer inch3Ge300,
                       Integer defaultVehCnt, String upddat, String updusr) {
        this.cartype       = cartype;
        this.lengthM       = lengthM;
        this.widthM        = widthM;
        this.heightM       = heightM;
        this.loadTon       = loadTon;
        this.sortSeq       = sortSeq;
        this.palletHeightM = palletHeightM;
        this.palletCnt     = palletCnt;
        this.longAxisYn    = longAxisYn;
        this.inch12Lt300   = inch12Lt300;
        this.inch12Ge300   = inch12Ge300;
        this.inch3Lt300    = inch3Lt300;
        this.inch3Ge300    = inch3Ge300;
        this.defaultVehCnt = defaultVehCnt;
        this.upddat        = upddat;
        this.updusr        = updusr;
    }
}
