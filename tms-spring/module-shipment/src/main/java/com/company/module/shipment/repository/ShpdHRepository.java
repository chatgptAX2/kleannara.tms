package com.company.module.shipment.repository;

import com.company.module.shipment.entity.ShpdH;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * 출고 헤더 Repository (SHPDH)
 */
public interface ShpdHRepository extends JpaRepository<ShpdH, String> {

    /**
     * 출고가능한 최대 납품요청일 조회
     * (SHPDI에 아이템이 존재하는 SHPDH만 대상)
     */
    @Query(value = """
        SELECT MAX(h.RQSHPD)
        FROM SHPDH h
        WHERE EXISTS (SELECT 1 FROM SHPDI i WHERE i.SHPOKY = h.SHPOKY)
          AND h.RQSHPD IS NOT NULL AND TRIM(h.RQSHPD) != ''
        """, nativeQuery = true)
    Optional<String> findMaxRqshpd();

    /**
     * 창고별 WAREKY 목록
     */
    @Query(value = """
        SELECT DISTINCT WAREKY FROM SHPDH
        WHERE WAREKY IS NOT NULL AND TRIM(WAREKY) != ''
        ORDER BY WAREKY
        """, nativeQuery = true)
    List<String> findDistinctWareky();
}
