package com.company.module.dispatchconfig.repository;

import com.company.module.dispatchconfig.entity.DispatchConstraint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DispatchConstraintRepository extends JpaRepository<DispatchConstraint, Long> {

    List<DispatchConstraint> findAllByProfileIdAndIsActiveOrderBySortSeqAsc(Long profileId, Integer isActive);

    Optional<DispatchConstraint> findByConstraintIdAndOwnrky(Long constraintId, String ownrky);
}
