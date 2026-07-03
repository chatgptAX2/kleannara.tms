package com.company.module.dispatchconfig.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ds_dispatch_constraint")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class DispatchConstraint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CONSTRAINT_ID")
    private Long constraintId;

    @Column(name = "PROFILE_ID", nullable = false)
    private Long profileId;

    @Column(name = "OWNRKY", length = 20)
    private String ownrky;

    @Column(name = "CONSTRAINT_TYPE", length = 50)
    private String constraintType;

    @Column(name = "CONSTRAINT_KEY", length = 100)
    private String constraintKey;

    @Column(name = "CONSTRAINT_VAL", length = 200)
    private String constraintVal;

    @Column(name = "SORT_SEQ")
    private Integer sortSeq;

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

    public void update(String constraintVal, Integer isActive) {
        this.constraintVal = constraintVal;
        if (isActive != null) this.isActive = isActive;
        this.updatedAt = LocalDateTime.now();
    }

    public void delete() {
        this.isActive = 0;
        this.updatedAt = LocalDateTime.now();
    }
}
