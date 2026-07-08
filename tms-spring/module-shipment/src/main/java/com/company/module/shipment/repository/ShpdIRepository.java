package com.company.module.shipment.repository;

import com.company.module.shipment.entity.ShpdI;
import com.company.module.shipment.entity.ShpdIId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * 출고 아이템 Repository (SHPDI)
 */
public interface ShpdIRepository extends JpaRepository<ShpdI, ShpdIId> {

    /**
     * LOTA02(플랜트) 필터 옵션 조회 — 공백 제거
     */
    @Query(value = """
        SELECT DISTINCT LOTA02 FROM SHPDI
        WHERE LOTA02 IS NOT NULL AND TRIM(LOTA02) != ''
        ORDER BY LOTA02
        """, nativeQuery = true)
    List<String> findDistinctLota02();

    /**
     * SKUG05 목록 (품목그룹)
     */
    @Query(value = """
        SELECT CMCDVL AS value, CDESC1 AS label
        FROM CMCDV
        WHERE CMCDKY = 'SKUG05'
        ORDER BY CMCDVL
        """, nativeQuery = true)
    List<Object[]> findSkug05Options();
}
