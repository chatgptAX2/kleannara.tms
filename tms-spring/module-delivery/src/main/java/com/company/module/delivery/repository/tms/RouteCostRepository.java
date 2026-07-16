package com.company.module.delivery.repository.tms;

import com.company.module.delivery.entity.tms.RouteCost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 경로별 운송비 Repository (Oracle KNRAWMS — ROUTE_COST 테이블)
 *
 * ■ TmsJpaConfig 에서 관리 (Oracle KNRAWMS)
 *   - Oracle 문자열 연결: '%' || :param || '%'  (CONCAT 3인수 미지원 → ORA-00909)
 *   - OFFSET/FETCH NEXT 페이징 Oracle 정상 지원
 */
@Repository
public interface RouteCostRepository extends JpaRepository<RouteCost, Long> {

    List<RouteCost> findByWarekyAndPtnrky(String wareky, String ptnrky);

    @Query(value = """
        SELECT * FROM KNRAWMS.ROUTE_COST
        WHERE (:wareky IS NULL OR WAREKY = :wareky)
          AND (:ptnrky IS NULL OR PTNRKY LIKE '%' || :ptnrky || '%')
          AND (:cartype IS NULL OR CARTYPE LIKE '%' || :cartype || '%')
        ORDER BY WAREKY, PTNRKY, CARTYPE
        """, nativeQuery = true)
    List<Object[]> searchList(
        @Param("wareky")  String wareky,
        @Param("ptnrky")  String ptnrky,
        @Param("cartype") String cartype
    );
}
