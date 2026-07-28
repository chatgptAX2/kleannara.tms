package com.company.module.vehicle.repository.wms;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;

/**
 * VhcmaRepositoryCustom 구현 — 동적 WHERE (소프트파싱 제거).
 *
 * ■ DataSource: wmsPU (WmsJpaConfig, Oracle KNRAWMS)
 * ■ 값이 존재하는 조건만 WHERE 절 + 바인드 파라미터로 추가.
 *   기존: (:param IS NULL OR col = :param)  ← 항상 SQL 포함(소프트파싱)
 *   변경: 값 있을 때만  AND col = ?          ← 동적 조건
 */
public class VhcmaRepositoryImpl implements VhcmaRepositoryCustom {

    @PersistenceContext(unitName = "wmsPU")
    private EntityManager em;

    private static final String SELECT_SQL = "SELECT * FROM KNRAWMS.VHCMA";
    private static final String COUNT_SQL  = "SELECT COUNT(*) FROM KNRAWMS.VHCMA";
    private static final String ORDER_BY   = " ORDER BY SHIP_POINT ASC, VEHICLE_NO ASC";

    @Override
    @SuppressWarnings("unchecked")
    public Page<Object[]> searchPage(
            String shipPoint, String productGroup, String deliveryZone, String carrier,
            String vehicleType, String vehicleKind, String vehicleClass, String vehicleNo,
            Pageable pageable) {

        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();

        addEq(where, params, "SHIP_POINT",    shipPoint);
        addEq(where, params, "PRODUCT_GROUP", productGroup);
        addEq(where, params, "DELIVERY_ZONE", deliveryZone);
        addLike(where, params, "CARRIER",     carrier);
        addEq(where, params, "VEHICLE_TYPE",  vehicleType);
        addEq(where, params, "VEHICLE_KIND",  vehicleKind);
        addEq(where, params, "VEHICLE_CLASS", vehicleClass);
        addLike(where, params, "VEHICLE_NO",  vehicleNo);

        // 1) 데이터 페이지 조회
        String pageSql = SELECT_SQL + where + ORDER_BY
                + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        Query dataQuery = em.createNativeQuery(pageSql);
        int idx = bind(dataQuery, params);
        dataQuery.setParameter(idx++, pageable.getOffset());
        dataQuery.setParameter(idx,   pageable.getPageSize());
        List<Object[]> rows = dataQuery.getResultList();

        // 2) 전체 건수 조회
        String countSql = COUNT_SQL + where;
        Query countQuery = em.createNativeQuery(countSql);
        bind(countQuery, params);
        Number total = (Number) countQuery.getSingleResult();

        return new PageImpl<>(rows, pageable, total.longValue());
    }

    private void addEq(StringBuilder where, List<Object> params, String col, String val) {
        if (val != null && !val.isEmpty()) {
            where.append(where.length() == 0 ? " WHERE" : " AND").append(' ').append(col).append(" = ?");
            params.add(val);
        }
    }

    private void addLike(StringBuilder where, List<Object> params, String col, String val) {
        if (val != null && !val.isEmpty()) {
            where.append(where.length() == 0 ? " WHERE" : " AND")
                 .append(' ').append(col).append(" LIKE '%' || ? || '%'");
            params.add(val);
        }
    }

    private int bind(Query q, List<Object> params) {
        int i = 1;
        for (Object p : params) q.setParameter(i++, p);
        return i;
    }
}
