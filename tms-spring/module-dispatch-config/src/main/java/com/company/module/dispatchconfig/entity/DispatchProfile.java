package com.company.module.dispatchconfig.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 배차제약 프로파일 (DS_DISPATCH_PROFILE)
 * Flask: api_dcon_profiles / api_dcon_profile_save / api_dcon_profile_delete
 */
@Entity
@Table(name = "ds_dispatch_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class DispatchProfile {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PROFILE_ID")
    private Long profileId;

    @Column(name = "PROFILE_NM", length = 100, nullable = false)
    private String profileNm;       // 프로파일명

    @Column(name = "PROFILE_DESC", length = 200)
    private String profileDesc;     // 설명

    @Column(name = "ACTIVE_YN", length = 1)
    private String activeYn;        // 활성 여부

    @Column(name = "SET_ID")
    private Integer setId;          // 연결된 const-set ID

    @Column(name = "SORT_SEQ")
    private Integer sortSeq;

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

    public void update(String profileNm, String profileDesc, Integer sortSeq) {
        if (profileNm   != null) this.profileNm   = profileNm;
        if (profileDesc != null) this.profileDesc = profileDesc;
        if (sortSeq     != null) this.sortSeq     = sortSeq;
    }

    public void delete() {
        this.activeYn = "N";
    }
}
