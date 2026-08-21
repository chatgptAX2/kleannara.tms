package com.company.module.delivery.repository.wms;

import com.company.module.delivery.entity.wms.BzptnDetail;
import com.company.module.delivery.entity.wms.BzptnDetailId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 납품처 TMS 상세 Repository — TMS DB (tmsPU)
 *
 * ■ DataSource: TmsJpaConfig (Oracle KNRATMS, tmsDataSource)
 *   TmsJpaConfig.basePackageClasses → BzptnDetailRepository.class
 *
 * ■ TMS/WMS 동일 DB/계정 (KNRATMS)
 *   BZPTN, BZPTN_DETAIL 모두 동일 Oracle DB (KNMESWMS) 소속.
 *   tmsDataSource(KNRATMS 계정)로 BZPTN JOIN BZPTN_DETAIL 직접 가능.
 *
 * ■ Oracle 문법 주의사항
 *   - 페이징: LIMIT/OFFSET(MySQL) → OFFSET ? ROWS FETCH NEXT ? ROWS ONLY (Oracle 12c+)
 *   - 문자열 연결: CONCAT('%', :p, '%')(MySQL) → '%' || :p || '%' (Oracle)
 *   - nativeQuery = true 이므로 Oracle SQL 문법을 직접 사용해야 함
 *
 * ■ 소프트파싱 제거
 *   - 기존 (:param IS NULL OR col ...) 고정조건 → BzptnDetailRepositoryImpl 의
 *     동적 WHERE(값 있을 때만 조건 추가) 로 전환.
 */
@Repository
public interface BzptnDetailRepository extends JpaRepository<BzptnDetail, BzptnDetailId>, BzptnDetailRepositoryCustom {

    Optional<BzptnDetail> findByPtnrkyAndPtnrtyAndOwnrky(
        String ptnrky, String ptnrty, String ownrky
    );

    boolean existsByPtnrkyAndPtnrtyAndOwnrky(
        String ptnrky, String ptnrty, String ownrky
    );
}
