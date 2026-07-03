package com.company.module.dispatchconfig.repository;

import com.company.module.dispatchconfig.entity.DispatchConstSet;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DispatchConstSetRepository extends JpaRepository<DispatchConstSet, Long> {

    List<DispatchConstSet> findAllByProfileIdAndIsActiveOrderByConstTypeAsc(Long profileId, Integer isActive);

    Optional<DispatchConstSet> findByConstIdAndOwnrky(Long constId, String ownrky);

    List<DispatchConstSet> findAllByProfileIdAndConstTypeAndIsActive(Long profileId, String constType, Integer isActive);
}
