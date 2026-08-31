package com.company.module.vehicle.repository.wms;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * VhcmaRepository 커스텀 프래그먼트 — 동적 WHERE 조회 (소프트파싱 제거).
 *
 * ■ 기존 (:param IS NULL OR col ...) 고정조건 → 값이 있을 때만 조건 추가하는 동적 SQL.
 */
public interface VhcmaRepositoryCustom {

    Page<Object[]> searchPage(
        String shipPoint,
        String productGroup,
        String deliveryZone,
        String carrier,
        String vehicleType,
        String vehicleKind,
        String vehicleClass,
        String vehicleNo,
        String useYn,
        Pageable pageable
    );
}
