package com.company.module.delivery.repository;

import com.company.module.delivery.entity.RouteCost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RouteCostRepository extends JpaRepository<RouteCost, Long> {

    List<RouteCost> findByWarekyAndPtnrky(String wareky, String ptnrky);

    @Query(value = """
        SELECT * FROM route_cost
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
