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

    // ※ SELECT * 는 컬럼 순서가 DB 물리순서라 프론트(컬럼명 접근)와 매핑이 어긋남.
    //   명시적 컬럼 목록으로 고정 → VehicleService 에서 동일 순서로 Map(컬럼명) 변환.
    // ※ VHCMA 물리 테이블에 미존재하는 컬럼(VHC_ID, CARTYPE, CARCLASS_CD) 제거 → ORA-00904 해소.
    //   - VHC_ID     : PK(시퀀스 VHCMA_SEQ). 조회 SELECT 대상 아님(엔티티 매핑 전용).
    //   - CARTYPE    : 차종명 → 실제 컬럼은 VEHICLE_KIND(차종)로 대체.
    //   - CARCLASS_CD: 차종(차형)코드 → 실제 컬럼은 VEHICLE_CLASS(차형)로 대체.
    public static final String[] COLS = {
        "VEHICLE_NO","OWNRKY","SHIP_POINT","PRODUCT_GROUP","DELIVERY_ZONE",
        "CARRIER","VEHICLE_TYPE","VEHICLE_KIND","VEHICLE_CLASS",
        "DRIVER_NAME","CONTACT_NO","PALLET_QTY","FLOOR_TYPE","USE_YN","OPERABLE_YN",
        "FIX_YN","DEL_YN","DLV_TIME_FROM","DLV_TIME_TO","VEHICLE_YEAR",
        "DELIVERY_CUSTOMER_1","DELIVERY_CUSTOMER_2",
        "CREDAT","CRETIM","CREUSR","LMODAT","LMOTIM","LMOUSR"
    };
    private static final String SELECT_SQL = "SELECT " + String.join(",", COLS) + " FROM KNRAWMS.VHCMA";
    private static final String COUNT_SQL  = "SELECT COUNT(*) FROM KNRAWMS.VHCMA";
    private static final String ORDER_BY   = " ORDER BY SHIP_POINT ASC, VEHICLE_NO ASC";

    @Override
    @SuppressWarnings("unchecked")
    public Page<Object[]> searchPage(
            String shipPoint, String productGroup, String deliveryZone, String carrier,
            String vehicleType, String vehicleKind, String vehicleClass, String vehicleNo,
            String useYn, Pageable pageable) {

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

        // 사용여부(=삭제여부 반대개념) 필터
        //   요청: 사용여부의 의미를 '삭제여부'로 변경 (Y:사용, N:미사용)
        //   VHCMA 물리 컬럼 DEL_YN 기준으로 판단 → DEL_YN='N'(또는 NULL) = 사용, DEL_YN='Y' = 미사용
        if (useYn != null && !useYn.isEmpty()) {
            if ("Y".equalsIgnoreCase(useYn)) {
                // 사용 = 삭제되지 않은 행 (DEL_YN != 'Y')
                where.append(where.length() == 0 ? " WHERE" : " AND")
                     .append(" (DEL_YN IS NULL OR DEL_YN <> 'Y')");
            } else if ("N".equalsIgnoreCase(useYn)) {
                // 미사용 = 삭제된 행 (DEL_YN = 'Y')
                where.append(where.length() == 0 ? " WHERE" : " AND")
                     .append(" DEL_YN = 'Y'");
            }
        }

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
