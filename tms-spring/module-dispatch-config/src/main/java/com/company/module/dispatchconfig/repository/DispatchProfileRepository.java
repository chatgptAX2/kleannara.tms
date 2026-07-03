package com.company.module.dispatchconfig.repository;

import com.company.module.dispatchconfig.entity.DispatchProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface DispatchProfileRepository extends JpaRepository<DispatchProfile, Long> {

    List<DispatchProfile> findAllByOwnrkyOrderBySortSeqAsc(String ownrky);

    Optional<DispatchProfile> findByProfileIdAndOwnrky(Long profileId, String ownrky);

    @Query(value = "SELECT COALESCE(MAX(SORT_SEQ),0)+1 FROM ds_dispatch_profile WHERE OWNRKY=:ownrky", nativeQuery = true)
    int nextSortSeq(@Param("ownrky") String ownrky);

    @Modifying
    @Query(value = "UPDATE ds_dispatch_profile SET SORT_SEQ=:seq WHERE PROFILE_ID=:id", nativeQuery = true)
    void updateSortSeq(@Param("id") Long id, @Param("seq") int seq);
}
