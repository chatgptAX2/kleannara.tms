package com.company.module.dispatch.repository;

import com.company.module.dispatch.entity.PsDispatchH;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PsDispatchHRepository extends JpaRepository<PsDispatchH, String> {

    /** 배차번호 PREFIX 기반 당일 최대번호 조회 (채번용) */
    @Query(value = """
        SELECT MAX(DISPATCH_NO)
        FROM KNRAWMS.PS_DISPATCH_H
        WHERE DISPATCH_NO LIKE CONCAT(:prefix, '%')
        """, nativeQuery = true)
    Optional<String> findMaxDispatchNoByPrefix(@Param("prefix") String prefix);

    /** 검색 조건 기반 목록 조회 */
    @Query(value = """
        SELECT h.*, COALESCE(v.LOAD_TON, 0) AS LOAD_TON
        FROM KNRAWMS.PS_DISPATCH_H h
        LEFT JOIN KNRAWMS.DS_VEHICLE v ON v.CARTYPE = h.CARTYPE
        WHERE (:dateFrom IS NULL OR h.RQSHPD >= :dateFrom)
          AND (:dateTo   IS NULL OR h.RQSHPD <= :dateTo)
          AND (:dptnky   IS NULL OR h.DPTNKY LIKE CONCAT('%',:dptnky,'%')
                                 OR h.DPTNM  LIKE CONCAT('%',:dptnky,'%'))
          AND (:status   IS NULL OR h.STATUS = :status)
          AND (:dispatchNo IS NULL OR h.DISPATCH_NO LIKE CONCAT('%',:dispatchNo,'%'))
        ORDER BY h.RQSHPD DESC, h.DISPATCH_NO
        """, nativeQuery = true)
    List<Object[]> searchList(
        @Param("dateFrom")   String dateFrom,
        @Param("dateTo")     String dateTo,
        @Param("dptnky")     String dptnky,
        @Param("status")     String status,
        @Param("dispatchNo") String dispatchNo
    );

    /** 납품요청일 + 납품처 기준 조회 (자동배차 중복 체크용) */
    List<PsDispatchH> findByRqshpdAndDptnkyAndStatCdNot(
        String rqshpd, String dptnky, String statCd
    );
}
