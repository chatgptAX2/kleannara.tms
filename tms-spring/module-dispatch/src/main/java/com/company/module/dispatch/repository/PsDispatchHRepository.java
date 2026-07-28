package com.company.module.dispatch.repository;

import com.company.module.dispatch.entity.PsDispatchH;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PsDispatchHRepository extends JpaRepository<PsDispatchH, String>, PsDispatchHRepositoryCustom {

    /** 배차번호 PREFIX 기반 당일 최대번호 조회 (채번용) */
    @Query(value = """
        SELECT MAX(DISPATCH_NO)
        FROM KNRAWMS.PS_DISPATCH_H
        WHERE DISPATCH_NO LIKE :prefix || '%'
        """, nativeQuery = true)
    Optional<String> findMaxDispatchNoByPrefix(@Param("prefix") String prefix);

    // 검색 조건 기반 목록 조회(searchList)는 소프트파싱 제거를 위해
    // PsDispatchHRepositoryImpl 의 동적 WHERE 로 이관됨.

    /** 납품요청일 + 납품처 기준 조회 (자동배차 중복 체크용) */
    List<PsDispatchH> findByRqshpdAndDptnkyAndStatCdNot(
        String rqshpd, String dptnky, String statCd
    );
}
