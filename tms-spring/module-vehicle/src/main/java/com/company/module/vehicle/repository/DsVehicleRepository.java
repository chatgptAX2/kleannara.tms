package com.company.module.vehicle.repository;

import com.company.module.vehicle.entity.DsVehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DsVehicleRepository extends JpaRepository<DsVehicle, String> {

    List<DsVehicle> findAllByOrderBySortSeqAsc();

    Optional<DsVehicle> findByCartype(String cartype);

    @Query("SELECT COALESCE(MAX(d.sortSeq), 0) + 1 FROM DsVehicle d")
    int nextSortSeq();
}
