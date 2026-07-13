package com.company.module.vehicle.repository.tms;

import com.company.module.vehicle.entity.tms.DsVehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * DS_VEHICLE Repository — MariaDB (tmsPU)
 *
 * ■ DataSource: TmsJpaConfig (MariaDB integration DB)
 *   TmsJpaConfig.basePackageClasses → DsVehicleRepository.class
 *
 * ※ vehicle.repository.tms 서브패키지로 분리한 이유:
 *    vehicle.repository (상위) 에 두면 WmsJpaConfig가 vehicle.repository.wms 를 스캔할 때
 *    basePackageClasses 재귀 스캔으로 이 클래스까지 포함되어 빈 중복 등록 오류 발생.
 *    → tms/wms 서브패키지를 완전히 분리하여 각 Config가 겹치지 않도록 구조화.
 */
@Repository
public interface DsVehicleRepository extends JpaRepository<DsVehicle, String> {

    List<DsVehicle> findAllByOrderBySortSeqAsc();

    Optional<DsVehicle> findByCartype(String cartype);

    @Query("SELECT COALESCE(MAX(d.sortSeq), 0) + 1 FROM DsVehicle d")
    int nextSortSeq();
}
