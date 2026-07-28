package com.company.module.delivery.repository.wms;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * BzptnDetailRepositoryCustom 구현 — 동적 WHERE (소프트파싱 제거).
 *
 * ■ DataSource: tmsPU (TmsJpaConfig, Oracle KNRAWMS — BZPTN/BZPTN_DETAIL 동일 DB)
 * ■ 값이 존재하는 조건만 WHERE 절 + 바인드 파라미터로 추가.
 *   기존: (:param IS NULL OR col = :param)  ← 항상 SQL 포함(소프트파싱)
 *   변경: 값 있을 때만  AND col = ?          ← 동적 조건
 */
public class BzptnDetailRepositoryImpl implements BzptnDetailRepositoryCustom {

    @PersistenceContext(unitName = "tmsPU")
    private EntityManager em;

    private static final String SELECT_SQL =
        "SELECT b.PTNRKY, b.NAME01, b.PTNRTY, b.OWNRKY, " +
        "       b.ADDR01, b.ADDR02, b.REGN01, b.TELN01, " +
        "       d.WAREKY, d.ROUTE_CD, d.ITEM_GROUP, d.AREA_CD, " +
        "       d.UNLOAD_TIME, d.MAX_HEIGHT, d.AUTO_ALLOC_YN, d.FORKLIFT_YN, " +
        "       d.INB_TIME_FROM1, d.INB_TIME_TO1, d.MAX_BOX_QTY, d.DEADLINE_TIME, d.MAX_TON, " +
        "       CASE WHEN d.PTNRKY IS NOT NULL THEN 'Y' ELSE 'N' END AS HAS_DETAIL " +
        "FROM KNRAWMS.BZPTN b " +
        "LEFT JOIN KNRAWMS.BZPTN_DETAIL d ON b.PTNRKY=d.PTNRKY AND b.PTNRTY=d.PTNRTY AND b.OWNRKY=d.OWNRKY";

    private static final String COUNT_SQL =
        "SELECT COUNT(*) " +
        "FROM KNRAWMS.BZPTN b " +
        "LEFT JOIN KNRAWMS.BZPTN_DETAIL d ON b.PTNRKY=d.PTNRKY AND b.PTNRTY=d.PTNRTY AND b.OWNRKY=d.OWNRKY";

    private static final String ORDER_BY = " ORDER BY b.PTNRKY";

    /** 공통 동적 WHERE 구성 (b.PTNRTY='CT' 고정조건 포함). */
    private String buildWhere(String wareky, String itemGroup, String ptnrky, String q, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE b.PTNRTY = 'CT'");
        if (wareky != null && !wareky.isEmpty()) {
            where.append(" AND d.WAREKY = ?");
            params.add(wareky);
        }
        if (itemGroup != null && !itemGroup.isEmpty()) {
            where.append(" AND d.ITEM_GROUP = ?");
            params.add(itemGroup);
        }
        if (ptnrky != null && !ptnrky.isEmpty()) {
            where.append(" AND (b.PTNRKY LIKE '%' || ? || '%' OR b.NAME01 LIKE '%' || ? || '%')");
            params.add(ptnrky);
            params.add(ptnrky);
        }
        if (q != null && !q.isEmpty()) {
            where.append(" AND (b.PTNRKY LIKE '%' || ? || '%'")
                 .append(" OR b.NAME01 LIKE '%' || ? || '%'")
                 .append(" OR b.ADDR01 LIKE '%' || ? || '%'")
                 .append(" OR b.REGN01 LIKE '%' || ? || '%')");
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
        }
        return where.toString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> searchList(String wareky, String itemGroup, String ptnrky, String q, int size, int offset) {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(wareky, itemGroup, ptnrky, q, params);
        String sql = SELECT_SQL + where + ORDER_BY + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        Query query = em.createNativeQuery(sql);
        int i = 1;
        for (Object p : params) query.setParameter(i++, p);
        query.setParameter(i++, offset);
        query.setParameter(i,   size);
        return query.getResultList();
    }

    @Override
    public long searchCount(String wareky, String itemGroup, String ptnrky, String q) {
        List<Object> params = new ArrayList<>();
        String where = buildWhere(wareky, itemGroup, ptnrky, q, params);
        String sql = COUNT_SQL + where;

        Query query = em.createNativeQuery(sql);
        int i = 1;
        for (Object p : params) query.setParameter(i++, p);
        Number total = (Number) query.getSingleResult();
        return total.longValue();
    }
}
