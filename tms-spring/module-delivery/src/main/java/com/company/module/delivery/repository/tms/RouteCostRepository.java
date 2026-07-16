package com.company.module.delivery.repository.tms;

import com.company.module.delivery.entity.tms.RouteCost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
 * ■ Oracle 주의사항
 *   - CONCAT()은 인수 2개만 허용 → LIKE '%'||:param||'%' 패턴 사용
 *   - NULL 소프트파싱: (:param IS NULL OR col LIKE '%'||:param||'%')
 */
@Repository
public interface RouteCostRepository extends JpaRepository<RouteCost, Long> {

    List<RouteCost> findByWarekyAndPtnrky(String wareky, String ptnrky);

    @Query(value = """
        SELECT
          COST_ID,
          WAREKY        AS SHPPT,
          PTNRKY        AS PTNRKY,
          CARTYPE       AS CARCLASS,
          COST_AMT      AS COST,
          DIST_KM       AS DIST_KM,
          EFF_DATE      AS DATE_START,
          EXP_DATE      AS DATE_END,
          'KRW'         AS UNIT
        FROM ROUTE_COST
        WHERE (:wareky   IS NULL OR WAREKY  = :wareky)
          AND (:ptnrky   IS NULL OR PTNRKY  LIKE '%' || :ptnrky  || '%')
          AND (:carclass IS NULL OR CARTYPE LIKE '%' || :carclass || '%')
        ORDER BY WAREKY, PTNRKY, CARTYPE
        """, nativeQuery = true)
    List<Object[]> searchList(
        @Param("wareky")   String wareky,
        @Param("ptnrky")   String ptnrky,
        @Param("carclass") String carclass
    );
}
