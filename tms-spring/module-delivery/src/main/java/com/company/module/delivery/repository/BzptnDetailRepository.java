package com.company.module.delivery.repository;

import com.company.module.delivery.entity.BzptnDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BzptnDetailRepository extends JpaRepository<BzptnDetail, Long> {

    Optional<BzptnDetail> findByPtnrkyAndPtnrtyAndOwnrky(
        String ptnrky, String ptnrty, String ownrky
    );

    boolean existsByPtnrkyAndPtnrtyAndOwnrky(
        String ptnrky, String ptnrty, String ownrky
    );

    /** 납품처 목록 (BZPTN JOIN 포함) */
    @Query(value = """
        SELECT b.PTNRKY, b.NAME01, b.PTNRTY, b.OWNRKY,
               b.ADDR01, b.ADDR02, b.REGN01, b.TELN01,
               d.WAREKY, d.ROUTE_CD, d.ITEM_GROUP, d.AREA_CD,
               d.UNLOAD_TIME, d.MAX_HEIGHT, d.AUTO_ALLOC_YN, d.FORKLIFT_YN,
               d.INB_TIME_FROM1, d.INB_TIME_TO1, d.MAX_BOX_QTY, d.DEADLINE_TIME, d.MAX_TON,
               CASE WHEN d.PTNRKY IS NOT NULL THEN 'Y' ELSE 'N' END AS HAS_DETAIL
        FROM BZPTN b
        LEFT JOIN bzptn_detail d ON b.PTNRKY=d.PTNRKY AND b.PTNRTY=d.PTNRTY AND b.OWNRKY=d.OWNRKY
        WHERE b.PTNRTY = 'CT'
          AND (:wareky   IS NULL OR d.WAREKY = :wareky)
          AND (:itemGroup IS NULL OR d.ITEM_GROUP = :itemGroup)
          AND (:ptnrky   IS NULL OR b.PTNRKY LIKE CONCAT('%',:ptnrky,'%')
                                 OR b.NAME01  LIKE CONCAT('%',:ptnrky,'%'))
          AND (:q        IS NULL OR b.PTNRKY LIKE CONCAT('%',:q,'%')
                                 OR b.NAME01  LIKE CONCAT('%',:q,'%')
                                 OR b.ADDR01  LIKE CONCAT('%',:q,'%')
                                 OR b.REGN01  LIKE CONCAT('%',:q,'%'))
        ORDER BY b.PTNRKY
        LIMIT :size OFFSET :offset
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
        FROM BZPTN b
        LEFT JOIN bzptn_detail d ON b.PTNRKY=d.PTNRKY AND b.PTNRTY=d.PTNRTY AND b.OWNRKY=d.OWNRKY
        WHERE b.PTNRTY = 'CT'
          AND (:wareky   IS NULL OR d.WAREKY = :wareky)
          AND (:itemGroup IS NULL OR d.ITEM_GROUP = :itemGroup)
          AND (:ptnrky   IS NULL OR b.PTNRKY LIKE CONCAT('%',:ptnrky,'%')
                                 OR b.NAME01  LIKE CONCAT('%',:ptnrky,'%'))
          AND (:q        IS NULL OR b.PTNRKY LIKE CONCAT('%',:q,'%')
                                 OR b.NAME01  LIKE CONCAT('%',:q,'%')
                                 OR b.ADDR01  LIKE CONCAT('%',:q,'%')
                                 OR b.REGN01  LIKE CONCAT('%',:q,'%'))
        """, nativeQuery = true)
    long searchCount(
        @Param("wareky")    String wareky,
        @Param("itemGroup") String itemGroup,
        @Param("ptnrky")    String ptnrky,
        @Param("q")         String q
    );
}
