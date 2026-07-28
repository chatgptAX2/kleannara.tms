package com.company.module.delivery.repository.tms;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * RouteCostRepositoryCustom 구현 — 동적 WHERE (소프트파싱 제거).
 *
 * ■ DataSource: tmsPU (TmsJpaConfig, Oracle KNRAWMS)
 * ■ 값이 존재하는 조건만 WHERE 절 + 바인드 파라미터로 추가한다.
 *   기존: (:param IS NULL OR col = :param)  ← 항상 SQL 포함(소프트파싱)
 *   변경: 값 있을 때만  AND col = ?          ← 동적 조건
 */
public class RouteCostRepositoryImpl implements RouteCostRepositoryCustom {

    @PersistenceContext(unitName = "tmsPU")
    private EntityManager em;

    private static final String BASE_SQL =
        "SELECT " +
        "  COST_ID, " +
        "  WAREKY   AS SHPPT, " +
        "  PTNRKY   AS PTNRKY, " +
        "  CARTYPE  AS CARCLASS, " +
        "  COST_AMT AS COST, " +
        "  DIST_KM  AS DIST_KM, " +
        "  EFF_DATE AS DATE_START, " +
        "  EXP_DATE AS DATE_END, " +
        "  'KRW'    AS UNIT " +
        "FROM KNRAWMS.ROUTE_COST";

    private static final String ORDER_BY = " ORDER BY WAREKY, PTNRKY, CARTYPE";

    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> searchList(String wareky, String ptnrky, String carclass) {
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (wareky != null && !wareky.isEmpty()) {
            where.append(where.length() == 0 ? " WHERE" : " AND").append(" WAREKY = ?");
            params.add(wareky);
        }
        if (ptnrky != null && !ptnrky.isEmpty()) {
            where.append(where.length() == 0 ? " WHERE" : " AND").append(" PTNRKY LIKE '%' || ? || '%'");
            params.add(ptnrky);
        }
        if (carclass != null && !carclass.isEmpty()) {
            where.append(where.length() == 0 ? " WHERE" : " AND").append(" CARTYPE LIKE '%' || ? || '%'");
            params.add(carclass);
        }

        String sql = BASE_SQL + where + ORDER_BY;
        Query q = em.createNativeQuery(sql);
        for (int i = 0; i < params.size(); i++) {
            q.setParameter(i + 1, params.get(i));
        }
        return q.getResultList();
    }
}
