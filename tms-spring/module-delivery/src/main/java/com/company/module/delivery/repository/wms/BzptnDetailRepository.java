package com.company.module.delivery.repository.wms;

import com.company.module.delivery.entity.wms.BzptnDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 납품처 TMS 상세 Repository — TMS DB (tmsPU)
 *
 * ■ DataSource: TmsJpaConfig (Oracle KNRAWMS, tmsDataSource)
 *   TmsJpaConfig.basePackageClasses → BzptnDetailRepository.class
 *
 * ■ BZPTN_DETAIL 위치
 *   BZPTN_DETAIL 은 WMS Oracle DB 가 아닌 TMS Oracle DB (KNRAWMS) 소속.
 *   WMS DB 의 BZPTN 과 Cross-DB JOIN 불가 → 2-step 분리:
 *     Step 1: wmsEm  → KNRAWMS.BZPTN  (NAME01, ADDR01, REGN01, TELN01 등)
 *     Step 2: tmsEm  → KNRAWMS.BZPTN_DETAIL (나머지 TMS 상세 정보)
 *   Java에서 PTNRKY 기준으로 merge.
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

    /**
     * BZPTN_DETAIL 목록 조회 — TMS DB 단독 (BZPTN JOIN 제거)
     * BZPTN(WMS DB) 와 Cross-DB JOIN 불가 → DeliveryService 에서 2-step merge
     */
    @Query(value = """
        SELECT d.PTNRKY, d.PTNRTY, d.OWNRKY,
               d.WAREKY, d.ROUTE_CD, d.ITEM_GROUP, d.AREA_CD,
               d.UNLOAD_TIME, d.MAX_HEIGHT, d.AUTO_ALLOC_YN, d.FORKLIFT_YN,
               d.INB_TIME_FROM1, d.INB_TIME_TO1, d.MAX_BOX_QTY, d.DEADLINE_TIME, d.MAX_TON
        FROM KNRAWMS.BZPTN_DETAIL d
        WHERE d.PTNRTY = 'CT'
          AND (:wareky    IS NULL OR d.WAREKY    = :wareky)
          AND (:itemGroup IS NULL OR d.ITEM_GROUP = :itemGroup)
        ORDER BY d.PTNRKY
        OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
        """, nativeQuery = true)
    java.util.List<Object[]> searchList(
        @Param("wareky")    String wareky,
        @Param("itemGroup") String itemGroup,
        @Param("size")      int size,
        @Param("offset")    int offset
    );

    @Query(value = """
        SELECT COUNT(*)
        FROM KNRAWMS.BZPTN_DETAIL d
        WHERE d.PTNRTY = 'CT'
          AND (:wareky    IS NULL OR d.WAREKY    = :wareky)
          AND (:itemGroup IS NULL OR d.ITEM_GROUP = :itemGroup)
        """, nativeQuery = true)
    long searchCount(
        @Param("wareky")    String wareky,
        @Param("itemGroup") String itemGroup
    );

    /** PTNRKY 목록으로 배치 조회 — DeliveryService 2-step merge 용 */
    @Query(value = """
        SELECT d.PTNRKY, d.PTNRTY, d.OWNRKY,
               d.WAREKY, d.ROUTE_CD, d.ITEM_GROUP, d.AREA_CD,
               d.UNLOAD_TIME, d.MAX_HEIGHT, d.AUTO_ALLOC_YN, d.FORKLIFT_YN,
               d.INB_TIME_FROM1, d.INB_TIME_TO1, d.MAX_BOX_QTY, d.DEADLINE_TIME, d.MAX_TON
        FROM KNRAWMS.BZPTN_DETAIL d
        WHERE d.PTNRKY IN (:ptnrkyList) AND d.PTNRTY = 'CT'
        """, nativeQuery = true)
    java.util.List<Object[]> findByPtnrkyIn(
        @Param("ptnrkyList") java.util.List<String> ptnrkyList
    );
}
