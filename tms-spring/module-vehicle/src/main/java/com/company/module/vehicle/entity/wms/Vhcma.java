package com.company.module.vehicle.entity.wms;

import jakarta.persistence.*;
import lombok.*;

/**
 * 차량 마스터 (KNRAWMS.VHCMA) – Oracle KNRAWMS 스키마
 * Flask: api_vehicle_list / api_vehicle_save / api_vehicle_delete 대응
 *
 * ■ DataSource: wmsPU (Oracle KNRAWMS)
 *   WmsJpaConfig.setPackagesToScan → com.company.module.vehicle.entity.wms
 */
@Entity
@Table(schema = "KNRAWMS", name = "VHCMA",
       uniqueConstraints = @UniqueConstraint(
           name = "UK_VHCMA", columnNames = {"VEHICLE_NO", "OWNRKY"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Vhcma {

    @Id @GeneratedValue(strategy = GenerationType.SEQUENCE,
                        generator = "VHCMA_SEQ")
    @SequenceGenerator(name = "VHCMA_SEQ",
                       sequenceName = "KNRAWMS.VHCMA_SEQ",
                       allocationSize = 1)
    @Column(name = "VEHICLE_ID")
    private Long vhcId;

    @Column(name = "VEHICLE_NO", length = 20, nullable = false)
    private String vehicleNo;

    @Column(name = "OWNRKY", length = 10)
    private String ownrky;

    @Column(name = "SHIP_POINT", length = 10)
    private String shipPoint;

    @Column(name = "PRODUCT_GROUP", length = 10)
    private String productGroup;

    @Column(name = "DELIVERY_ZONE", length = 20)
    private String deliveryZone;

    @Column(name = "CARRIER", length = 100)
    private String carrier;

    @Column(name = "VEHICLE_TYPE", length = 20)
    private String vehicleType;

    @Column(name = "VEHICLE_KIND", length = 20)
    private String vehicleKind;

    @Column(name = "VEHICLE_CLASS", length = 20)
    private String vehicleClass;

    @Column(name = "CARTYPE", length = 50)
    private String cartype;          // DS_VEHICLE 차종명

    @Column(name = "CARCLASS_CD", length = 20)
    private String carclassCd;       // DS_VEHICLE 차종코드

    @Column(name = "DRIVER_NAME", length = 50)
    private String driverName;

    @Column(name = "CONTACT_NO", length = 20)
    private String contactNo;

    @Column(name = "PALLET_QTY")
    private Integer palletQty;

    @Column(name = "FLOOR_TYPE", length = 10)
    private String floorType;

    @Column(name = "USE_YN", length = 1)
    private String useYn;

    @Column(name = "OPERABLE_YN", length = 1)
    private String operableYn;

    @Column(name = "FIX_YN", length = 1)
    private String fixYn;

    @Column(name = "DEL_YN", length = 1)
    private String delYn;

    @Column(name = "DLV_TIME_FROM", length = 6)
    private String dlvTimeFrom;

    @Column(name = "DLV_TIME_TO", length = 6)
    private String dlvTimeTo;

    @Column(name = "VEHICLE_YEAR", length = 4)
    private String vehicleYear;

    @Column(name = "DELIVERY_CUSTOMER_1", length = 20)
    private String deliveryCustomer1;

    @Column(name = "DELIVERY_CUSTOMER_2", length = 20)
    private String deliveryCustomer2;

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
