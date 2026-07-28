package com.company.module.dispatch.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * PsDispatchHRepositoryCustom 구현 — 동적 WHERE (소프트파싱 제거).
 *
 * ■ DataSource: tmsPU (TmsJpaConfig, Oracle KNRAWMS)
 * ■ 값이 존재하는 조건만 WHERE 절 + 바인드 파라미터로 추가.
 *   기존: (:param IS NULL OR col = :param)  ← 항상 SQL 포함(소프트파싱)
 *   변경: 값 있을 때만  AND col = ?          ← 동적 조건
 */
public class PsDispatchHRepositoryImpl implements PsDispatchHRepositoryCustom {

    @PersistenceContext(unitName = "tmsPU")
    private EntityManager em;

    private static final String BASE_SQL =
        "SELECT h.*, COALESCE(v.LOAD_TON, 0) AS LOAD_TON " +
        "FROM KNRAWMS.PS_DISPATCH_H h " +
        "LEFT JOIN KNRAWMS.DS_VEHICLE v ON v.CARTYPE = h.CARTYPE";

    private static final String ORDER_BY = " ORDER BY h.RQSHPD DESC, h.DISPATCH_NO";

    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> searchList(String dateFrom, String dateTo, String dptnky, String status, String dispatchNo) {
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (dateFrom != null && !dateFrom.isEmpty()) {
            where.append(where.length() == 0 ? " WHERE" : " AND").append(" h.RQSHPD >= ?");
            params.add(dateFrom);
        }
        if (dateTo != null && !dateTo.isEmpty()) {
            where.append(where.length() == 0 ? " WHERE" : " AND").append(" h.RQSHPD <= ?");
            params.add(dateTo);
        }
        if (dptnky != null && !dptnky.isEmpty()) {
            where.append(where.length() == 0 ? " WHERE" : " AND")
                 .append(" (h.DPTNKY LIKE '%' || ? || '%' OR h.DPTNM LIKE '%' || ? || '%')");
            params.add(dptnky);
            params.add(dptnky);
        }
        if (status != null && !status.isEmpty()) {
            where.append(where.length() == 0 ? " WHERE" : " AND").append(" h.STATUS = ?");
            params.add(status);
        }
        if (dispatchNo != null && !dispatchNo.isEmpty()) {
            where.append(where.length() == 0 ? " WHERE" : " AND").append(" h.DISPATCH_NO LIKE '%' || ? || '%'");
            params.add(dispatchNo);
        }

        String sql = BASE_SQL + where + ORDER_BY;
        Query q = em.createNativeQuery(sql);
        for (int i = 0; i < params.size(); i++) {
            q.setParameter(i + 1, params.get(i));
        }
        return q.getResultList();
    }
}
