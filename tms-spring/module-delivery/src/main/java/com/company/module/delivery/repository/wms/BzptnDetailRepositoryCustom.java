package com.company.module.delivery.repository.wms;

import java.util.List;

/**
 * BzptnDetailRepository 커스텀 프래그먼트 — 동적 WHERE 조회 (소프트파싱 제거).
 *
 * ■ 기존 (:param IS NULL OR col ...) 고정조건 → 값이 있을 때만 조건 추가하는 동적 SQL.
 */
public interface BzptnDetailRepositoryCustom {

    /** 납품처 목록 조회 — 값이 존재하는 조건만 동적으로 적용 + Oracle 페이징. */
    List<Object[]> searchList(String wareky, String itemGroup, String ptnrky, String q, int size, int offset);

    /** 납품처 건수 조회 — searchList 와 동일 WHERE. */
    long searchCount(String wareky, String itemGroup, String ptnrky, String q);
}
