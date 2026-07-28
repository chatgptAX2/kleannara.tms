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
     *
     * ■ 성능 최적화 (기존 MAX + EXISTS 전건 스캔 → RQSHPD 내림차순 조기종료)
     *   기존: SELECT MAX(RQSHPD) 는 EXISTS 상관 서브쿼리를 SHPDH '모든' 행에 대해
     *         평가해야 최댓값을 확정할 수 있어 대용량에서 20초↑ 소요.
     *   변경: RQSHPD 내림차순으로 정렬하며 아이템이 존재하는 첫 행에서 ROWNUM=1 로 즉시 종료.
     *         SHPDH(RQSHPD) 인덱스의 내림차순 range scan + SHPDI(SHPOKY) 로 EXISTS 를
     *         한 행씩 확인하다 최초 매칭에서 멈추므로 전건 스캔이 사라진다.
     *   ※ RQSHPD 는 'YYYYMMDD' 문자열. NULL/공백 제외는 RQSHPD >= '00000001' 범위조건으로
     *     처리( '<> '' '' ' 대비 인덱스 친화적).
     */
    @Query(value = """
        SELECT RQSHPD FROM (
            SELECT h.RQSHPD
            FROM KNRAWMS.SHPDH h
            WHERE h.RQSHPD >= '00000001'
              AND EXISTS (SELECT 1 FROM KNRAWMS.SHPDI i WHERE i.SHPOKY = h.SHPOKY)
            ORDER BY h.RQSHPD DESC
        )
        WHERE ROWNUM = 1
        """, nativeQuery = true)
    Optional<String> findMaxRqshpd();

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
