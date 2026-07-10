package com.company.module.delivery.repository;

import com.company.module.delivery.entity.BzptnDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 납품처 Repository — Oracle WMS DB (wmsPU) 로 실행됨
 *
 * ■ Oracle 문법 주의사항
 *   - 페이징: LIMIT/OFFSET(MySQL) → OFFSET ? ROWS FETCH NEXT ? ROWS ONLY (Oracle 12c+)
 *   - 문자열 연결: CONCAT('%', :p, '%')(MySQL) → '%' || :p || '%' (Oracle)
 *   - nativeQuery = true 이므로 Oracle SQL 문법을 직접 사용해야 함
 */
@Repository
public interface BzptnDetailRepository extends JpaRepository<BzptnDetail, Long> {

    Optional<BzptnDetail> findByPtnrkyAndPtnrtyAndOwnrky(
        String ptnrky, String ptnrty, String ownrky
    );

    boolean existsByPtnrkyAndPtnrtyAndOwnrky(
        String ptnrky, String ptnrty, String ownrky
    );

    /** 납품처 목록 (BZPTN JOIN 포함) — Oracle 12c+ 페이징 */
    @Query(value = """
        SELECT b.PTNRKY, b.NAME01, b.PTNRTY, b.OWNRKY,
               b.ADDR01, b.ADDR02, b.REGN01, b.TELN01,
               d.WAREKY, d.ROUTE_CD, d.ITEM_GROUP, d.AREA_CD,
               d.UNLOAD_TIME, d.MAX_HEIGHT, d.AUTO_ALLOC_YN, d.FORKLIFT_YN,
               d.INB_TIME_FROM1, d.INB_TIME_TO1, d.MAX_BOX_QTY, d.DEADLINE_TIME, d.MAX_TON,
               CASE WHEN d.PTNRKY IS NOT NULL THEN 'Y' ELSE 'N' END AS HAS_DETAIL
        FROM KNRAWMS.BZPTN b
        LEFT JOIN KNRAWMS.BZPTN_DETAIL d ON b.PTNRKY=d.PTNRKY AND b.PTNRTY=d.PTNRTY AND b.OWNRKY=d.OWNRKY
        WHERE b.PTNRTY = 'CT'
          AND (:wareky   IS NULL OR d.WAREKY = :wareky)
          AND (:itemGroup IS NULL OR d.ITEM_GROUP = :itemGroup)
          AND (:ptnrky   IS NULL OR b.PTNRKY LIKE '%' || :ptnrky || '%'
                                 OR b.NAME01  LIKE '%' || :ptnrky || '%')
          AND (:q        IS NULL OR b.PTNRKY LIKE '%' || :q || '%'
                                 OR b.NAME01  LIKE '%' || :q || '%'
                                 OR b.ADDR01  LIKE '%' || :q || '%'
                                 OR b.REGN01  LIKE '%' || :q || '%')
        ORDER BY b.PTNRKY
        OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
        """, nativeQuery = true)
    java.util.List<Object[]> searchList(
        @Param("wareky")    String wareky,
        @Param("itemGroup") String itemGroup,
        @Param("ptnrky")    String ptnrky,
        @Param("q")         String q,
        @Param("size")      int size,
        @Param("offset")    int offset
    );

    @Query(value = """
        SELECT COUNT(*)
        FROM KNRAWMS.BZPTN b
        LEFT JOIN KNRAWMS.BZPTN_DETAIL d ON b.PTNRKY=d.PTNRKY AND b.PTNRTY=d.PTNRTY AND b.OWNRKY=d.OWNRKY
        WHERE b.PTNRTY = 'CT'
          AND (:wareky   IS NULL OR d.WAREKY = :wareky)
          AND (:itemGroup IS NULL OR d.ITEM_GROUP = :itemGroup)
          AND (:ptnrky   IS NULL OR b.PTNRKY LIKE '%' || :ptnrky || '%'
                                 OR b.NAME01  LIKE '%' || :ptnrky || '%')
          AND (:q        IS NULL OR b.PTNRKY LIKE '%' || :q || '%'
                                 OR b.NAME01  LIKE '%' || :q || '%'
                                 OR b.ADDR01  LIKE '%' || :q || '%'
                                 OR b.REGN01  LIKE '%' || :q || '%')
        """, nativeQuery = true)
    long searchCount(
        @Param("wareky")    String wareky,
        @Param("itemGroup") String itemGroup,
        @Param("ptnrky")    String ptnrky,
        @Param("q")         String q
    );
}
