package com.company.module.vehicle.repository.wms;

import com.company.module.vehicle.entity.wms.Vhcma;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * VHCMA 차량마스터 Repository — Oracle KNRAWMS
 *
 * ■ DataSource: wmsPU (Oracle KNRAWMS)
 *   WmsJpaConfig.basePackages → com.company.module.vehicle.repository.wms
 *
 * ■ 소프트파싱 제거
 *   - 기존 (:param IS NULL OR col ...) 고정조건 → VhcmaRepositoryImpl 의
 *     동적 WHERE(값 있을 때만 조건 추가) 로 전환.
 */
@Repository
public interface VhcmaRepository extends JpaRepository<Vhcma, String>, VhcmaRepositoryCustom {

    Optional<Vhcma> findByVehicleNoAndOwnrky(String vehicleNo, String ownrky);

    boolean existsByVehicleNoAndOwnrky(String vehicleNo, String ownrky);

    /** 필터용 유니크 목록 */
    @Query("SELECT DISTINCT v.productGroup FROM Vhcma v WHERE v.productGroup IS NOT NULL ORDER BY v.productGroup")
    List<String> findDistinctProductGroups();

    @Query("SELECT DISTINCT v.deliveryZone FROM Vhcma v WHERE v.deliveryZone IS NOT NULL ORDER BY v.deliveryZone")
    List<String> findDistinctDeliveryZones();

    @Query("SELECT DISTINCT v.carrier FROM Vhcma v WHERE v.carrier IS NOT NULL ORDER BY v.carrier")
    List<String> findDistinctCarriers();

    @Query("SELECT DISTINCT v.vehicleType FROM Vhcma v WHERE v.vehicleType IS NOT NULL ORDER BY v.vehicleType")
    List<String> findDistinctVehicleTypes();

    @Query("SELECT DISTINCT v.vehicleKind FROM Vhcma v WHERE v.vehicleKind IS NOT NULL ORDER BY v.vehicleKind")
    List<String> findDistinctVehicleKinds();

    @Query("SELECT DISTINCT v.vehicleClass FROM Vhcma v WHERE v.vehicleClass IS NOT NULL ORDER BY v.vehicleClass")
    List<String> findDistinctVehicleClasses();
}
