package com.company.module.dispatch.repository;

import com.company.module.dispatch.entity.PsDispatchI;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PsDispatchIRepository extends JpaRepository<PsDispatchI, Long> {

    /** 배차번호 기준 아이템 전체 조회 */
    List<PsDispatchI> findByDispatchNoOrderByItemId(String dispatchNo);

    /** 배차번호 복수 기준 아이템 조회 */
    List<PsDispatchI> findByDispatchNoIn(List<String> dispatchNos);

    /** 배차번호 기준 아이템 삭제 */
    void deleteByDispatchNo(String dispatchNo);

    /** 납품문서 + 라인으로 배차아이템 조회 (중복 체크) */
    boolean existsByShpokyAndShpoit(String shpoky, String shpoit);

    /** 아이템 상세 조회 (RECDI 조인, 롤 중량 포함)
     *  [원지 롤수 정확도] UNIT_WEIGHT(원지 1롤 단중)는 SKU별 최신 입고(STATIT='FRV',
     *  RECVKY 최신) 1건만 조인 — 단순 SKUKEY 조인 시 여러 입고건과 fan-out 되어
     *  D 행이 중복되고 단중이 비결정적으로 잡히는 것을 방지(searchDocs A안과 동일 취지). */
    @Query(value = """
        SELECT d.ITEM_ID, d.DISPATCH_NO, d.SEQ, d.SHPOKY, d.SHPOIT,
               d.SKUKEY, d.DESC01, d.QTSHPO, d.UOMKEY,
               d.DPTNKY, d.DPTNM, d.IS_SPLIT, d.ORG_SHPOKY, d.ORG_SHPOIT,
               COALESCE(d.GRSWGT,   0) AS GRSWGT,
               COALESCE(d.KG_WEIGHT,0) AS KG_WEIGHT,
               COALESCE(rd.QTYRCV,  0) AS UNIT_WEIGHT
        FROM KNRAWMS.TMS_PS_DISPATCH_D d
        LEFT JOIN (
            SELECT SKUKEY, QTYRCV FROM (
                SELECT r.SKUKEY, r.QTYRCV,
                       ROW_NUMBER() OVER (PARTITION BY r.SKUKEY ORDER BY r.RECVKY DESC) AS RN
                FROM KNRAWMS.RECDI r
                WHERE r.STATIT = 'FRV'
            ) WHERE RN = 1
        ) rd ON rd.SKUKEY = d.SKUKEY
        WHERE d.DISPATCH_NO = :dispatchNo
        ORDER BY d.SEQ
        """, nativeQuery = true)
    List<Object[]> findItemsWithUnitWeight(@Param("dispatchNo") String dispatchNo);
}
