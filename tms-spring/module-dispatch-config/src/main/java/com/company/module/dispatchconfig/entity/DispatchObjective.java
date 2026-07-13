package com.company.module.dispatchconfig.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 배차 목적식 (DS_DISPATCH_OBJECTIVE)
 * Flask: api_obj_list / api_obj_save / api_obj_delete / api_obj_activate / api_obj_active
 */
@Entity
@Table(name = "DS_DISPATCH_OBJECTIVE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class DispatchObjective {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "OBJ_ID")
    private Long objId;

    @Column(name = "OBJ_CODE", length = 30, nullable = false, unique = true)
    private String objCode;     // MIN_VEHICLES / MAX_FILL / MIN_COST

    @Column(name = "OBJ_NM", length = 100)
    private String objNm;       // 표시명

    @Column(name = "OBJ_ICON", length = 10)
    private String objIcon;     // 이모지 아이콘

    @Column(name = "OBJ_ALGO", length = 50)
    private String objAlgo;     // 알고리즘 코드

    @Column(name = "OBJ_DESC", length = 200)
    private String objDesc;     // 설명

    @Column(name = "SORT_SEQ")
    private Integer sortSeq;

    @Column(name = "ACTIVE_YN", length = 1)
    private String activeYn;    // Y = 활성(단일 보장)

    @Column(name = "CREDAT", length = 8)
    private String credat;

    @Column(name = "LMODAT", length = 8)
    private String lmodat;

    @PrePersist
    protected void onCreate() {
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        if (this.credat == null) this.credat = today;
        this.lmodat = today;
        if (this.activeYn == null) this.activeYn = "Y";
    }

    @PreUpdate
    protected void onUpdate() {
        this.lmodat = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    }

    public void activate()   { this.activeYn = "Y"; }
    public void deactivate() { this.activeYn = "N"; }

    public void update(String objCode, String objNm, String objIcon, String objAlgo,
                       String objDesc, Integer sortSeq, String activeYn) {
        this.objCode = objCode;
        this.objNm   = objNm;
        this.objIcon = objIcon;
        this.objAlgo = objAlgo;
        this.objDesc = objDesc;
        this.sortSeq = sortSeq;
        this.activeYn = activeYn;
    }
}
