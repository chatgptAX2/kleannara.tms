package com.company.module.delivery.repository.tms;

import com.company.module.delivery.entity.tms.RouteCost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 경로별 운송비 Repository (MariaDB TMS — route_cost 테이블)
 *
 * ■ TmsJpaConfig 에서 관리 (MariaDB)
 *   - CONCAT('%', :param, '%') MariaDB 정상 지원
 *   - LIMIT/OFFSET 페이징 MariaDB 정상 지원
 */
@Repository
public interface RouteCostRepository extends JpaRepository<RouteCost, Long> {

    List<RouteCost> findByWarekyAndPtnrky(String wareky, String ptnrky);

    @Query(value = """
        SELECT * FROM ROUTE_COST
        WHERE (:wareky IS NULL OR WAREKY = :wareky)
          AND (:ptnrky IS NULL OR PTNRKY LIKE CONCAT('%',:ptnrky,'%'))
          AND (:cartype IS NULL OR CARTYPE LIKE CONCAT('%',:cartype,'%'))
        ORDER BY WAREKY, PTNRKY, CARTYPE
        """, nativeQuery = true)
    List<Object[]> searchList(
        @Param("wareky")  String wareky,
        @Param("ptnrky")  String ptnrky,
        @Param("cartype") String cartype
    );
}
