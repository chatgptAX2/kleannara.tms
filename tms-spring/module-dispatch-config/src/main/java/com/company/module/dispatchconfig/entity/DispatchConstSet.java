package com.company.module.dispatchconfig.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "DS_DISPATCH_CONST_SET")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class DispatchConstSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CONST_ID")
    private Long constId;

    @Column(name = "PROFILE_ID", nullable = false)
    private Long profileId;

    @Column(name = "OWNRKY", length = 20)
    private String ownrky;

    @Column(name = "CONST_TYPE", length = 50)
    private String constType;

    @Column(name = "CARTYPE", length = 30)
    private String cartype;

    @Column(name = "REGION", length = 50)
    private String region;

    @Column(name = "CONST_VAL", length = 200)
    private String constVal;

    @Column(name = "IS_DYNAMIC")
    private Integer isDynamic;

    @Column(name = "FORKLIFT_YN", length = 1)
    private String forkliftYn;

    @Column(name = "ENTRY_TON")
    private Double entryTon;

    @Column(name = "IS_ACTIVE")
    private Integer isActive;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.isActive == null) this.isActive = 1;
    }

    public void update(String constVal, String cartype, String region, Integer isDynamic,
                       String forkliftYn, Double entryTon) {
        if (constVal != null) this.constVal = constVal;
        if (cartype != null) this.cartype = cartype;
        if (region != null) this.region = region;
        if (isDynamic != null) this.isDynamic = isDynamic;
        if (forkliftYn != null) this.forkliftYn = forkliftYn;
        if (entryTon != null) this.entryTon = entryTon;
        this.updatedAt = LocalDateTime.now();
    }

    public void delete() {
        this.isActive = 0;
        this.updatedAt = LocalDateTime.now();
    }
}
