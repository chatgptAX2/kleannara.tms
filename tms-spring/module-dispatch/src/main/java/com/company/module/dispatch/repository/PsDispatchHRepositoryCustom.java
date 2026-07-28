package com.company.module.dispatch.repository;

import java.util.List;

/**
 * PsDispatchHRepository 커스텀 프래그먼트 — 동적 WHERE 조회 (소프트파싱 제거).
 *
 * ■ 기존 (:param IS NULL OR col ...) 고정조건 → 값이 있을 때만 조건 추가하는 동적 SQL.
 */
public interface PsDispatchHRepositoryCustom {

    /** 검색 조건 기반 목록 조회 — 값이 존재하는 조건만 동적으로 적용. */
    List<Object[]> searchList(String dateFrom, String dateTo, String dptnky, String status, String dispatchNo);
}
