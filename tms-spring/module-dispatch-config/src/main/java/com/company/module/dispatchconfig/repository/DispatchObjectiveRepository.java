package com.company.module.dispatchconfig.repository;

import com.company.module.dispatchconfig.entity.DispatchObjective;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DispatchObjectiveRepository extends JpaRepository<DispatchObjective, Long> {
    List<DispatchObjective> findAllByOrderBySortSeqAscObjIdAsc();
    Optional<DispatchObjective> findByActiveYn(String activeYn);

    @Modifying
    @Query("UPDATE DispatchObjective d SET d.activeYn = 'N'")
    void deactivateAll();
}
