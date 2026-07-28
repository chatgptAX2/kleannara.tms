package com.company.module.delivery.repository.tms;

import com.company.module.delivery.entity.tms.RouteCost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 경로별 운송비 Repository (TMS Oracle 19C — KNRAWMS.ROUTE_COST 테이블)
 *
 * ■ TmsJpaConfig 에서 관리 (Oracle TmsDataSource)
 *   - searchList: 프론트엔드 기대 컬럼명으로 alias 반환
 *     WAREKY→SHPPT, PTNRKY→PTNRKY, CARTYPE→CARCLASS,
 *     COST_AMT→COST, DIST_KM→DIST_KM, EFF_DATE→DATE_START, EXP_DATE→DATE_END
 *
 * ■ 소프트파싱 제거
 *   - 기존 (:param IS NULL OR col ...) 고정조건 → RouteCostRepositoryImpl 의
 *     동적 WHERE(값 있을 때만 조건 추가) 로 전환.
 */
@Repository
public interface RouteCostRepository extends JpaRepository<RouteCost, Long>, RouteCostRepositoryCustom {

    List<RouteCost> findByWarekyAndPtnrky(String wareky, String ptnrky);
}
