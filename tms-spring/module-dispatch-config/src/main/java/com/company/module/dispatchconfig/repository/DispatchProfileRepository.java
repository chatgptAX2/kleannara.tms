package com.company.module.dispatchconfig.repository;

import com.company.module.dispatchconfig.entity.DispatchProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface DispatchProfileRepository extends JpaRepository<DispatchProfile, Long> {

    List<DispatchProfile> findAllByOrderBySortSeqAscProfileIdAsc();

    Optional<DispatchProfile> findByProfileIdAndActiveYn(Long profileId, String activeYn);

    @Query(value = "SELECT COALESCE(MAX(SORT_SEQ),0)+1 FROM KNRAWMS.DS_DISPATCH_PROFILE", nativeQuery = true)
    int nextSortSeq();

    @Modifying
    @Query(value = "UPDATE KNRAWMS.DS_DISPATCH_PROFILE SET SORT_SEQ=:seq WHERE PROFILE_ID=:id", nativeQuery = true)
    void updateSortSeq(@Param("id") Long id, @Param("seq") int seq);
}
