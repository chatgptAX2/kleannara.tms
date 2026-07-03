package com.company.module.vehicle.repository;

import com.company.module.vehicle.entity.Vhcma;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VhcmaRepository extends JpaRepository<Vhcma, Long> {

    Optional<Vhcma> findByVehicleNoAndOwnrky(String vehicleNo, String ownrky);

    boolean existsByVehicleNoAndOwnrky(String vehicleNo, String ownrky);

    @Query(value = """
        SELECT * FROM vhcma
        WHERE (:shipPoint IS NULL OR SHIP_POINT = :shipPoint)
          AND (:productGroup IS NULL OR PRODUCT_GROUP = :productGroup)
          AND (:deliveryZone IS NULL OR DELIVERY_ZONE = :deliveryZone)
          AND (:carrier IS NULL OR CARRIER LIKE CONCAT('%',:carrier,'%'))
          AND (:vehicleType IS NULL OR VEHICLE_TYPE = :vehicleType)
          AND (:vehicleKind IS NULL OR VEHICLE_KIND = :vehicleKind)
          AND (:vehicleClass IS NULL OR VEHICLE_CLASS = :vehicleClass)
          AND (:vehicleNo IS NULL OR VEHICLE_NO LIKE CONCAT('%',:vehicleNo,'%'))
        ORDER BY SHIP_POINT ASC, VEHICLE_NO ASC
        """, nativeQuery = true,
        countQuery = """
        SELECT COUNT(*) FROM vhcma
        WHERE (:shipPoint IS NULL OR SHIP_POINT = :shipPoint)
          AND (:productGroup IS NULL OR PRODUCT_GROUP = :productGroup)
          AND (:deliveryZone IS NULL OR DELIVERY_ZONE = :deliveryZone)
          AND (:carrier IS NULL OR CARRIER LIKE CONCAT('%',:carrier,'%'))
          AND (:vehicleType IS NULL OR VEHICLE_TYPE = :vehicleType)
          AND (:vehicleKind IS NULL OR VEHICLE_KIND = :vehicleKind)
          AND (:vehicleClass IS NULL OR VEHICLE_CLASS = :vehicleClass)
          AND (:vehicleNo IS NULL OR VEHICLE_NO LIKE CONCAT('%',:vehicleNo,'%'))
        """)
    Page<Object[]> searchPage(
        @Param("shipPoint")    String shipPoint,
        @Param("productGroup") String productGroup,
        @Param("deliveryZone") String deliveryZone,
        @Param("carrier")      String carrier,
        @Param("vehicleType")  String vehicleType,
        @Param("vehicleKind")  String vehicleKind,
        @Param("vehicleClass") String vehicleClass,
        @Param("vehicleNo")    String vehicleNo,
        Pageable pageable
    );

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
