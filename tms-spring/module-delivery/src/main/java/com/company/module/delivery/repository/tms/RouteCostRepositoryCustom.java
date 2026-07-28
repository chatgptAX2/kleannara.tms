package com.company.module.delivery.repository.tms;

import java.util.List;

/**
 * RouteCostRepository 커스텀 프래그먼트 — 동적 WHERE 조회
 *
 * ■ 소프트파싱 제거: 기존 (:param IS NULL OR col ...) 고정조건을
 *   값이 있을 때만 WHERE 절에 추가하는 동적 SQL 로 전환.
 */
public interface RouteCostRepositoryCustom {

    /**
     * 경로별 운송비 검색 — 값이 존재하는 조건만 동적으로 적용.
     * 반환 컬럼 순서: COST_ID(0),SHPPT(1),PTNRKY(2),CARCLASS(3),COST(4),
     *               DIST_KM(5),DATE_START(6),DATE_END(7),UNIT(8)
     */
    List<Object[]> searchList(String wareky, String ptnrky, String carclass);
}
