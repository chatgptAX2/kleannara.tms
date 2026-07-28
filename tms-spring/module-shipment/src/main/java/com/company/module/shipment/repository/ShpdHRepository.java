package com.company.module.shipment.repository;

import com.company.module.shipment.entity.ShpdH;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * 출고 헤더 Repository (SHPDH)
 */
public interface ShpdHRepository extends JpaRepository<ShpdH, String> {

    /**
     * 창고별 WAREKY 목록
     */
    @Query(value = """
        SELECT DISTINCT WAREKY FROM KNRAWMS.SHPDH
        WHERE WAREKY IS NOT NULL AND WAREKY <> ' '
        ORDER BY WAREKY
        """, nativeQuery = true)
    List<String> findDistinctWareky();
}
