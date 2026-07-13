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

    /** 아이템 상세 조회 (RECDI 조인, 롤 중량 포함) */
    @Query(value = """
        SELECT d.ITEM_ID, d.DISPATCH_NO, d.SEQ, d.SHPOKY, d.SHPOIT,
               d.SKUKEY, d.DESC01, d.QTSHPO, d.UOMKEY,
               d.DPTNKY, d.DPTNM, d.IS_SPLIT, d.ORG_SHPOKY, d.ORG_SHPOIT,
               COALESCE(d.GRSWGT,   0) AS GRSWGT,
               COALESCE(d.KG_WEIGHT,0) AS KG_WEIGHT,
               COALESCE(rd.QTYRCV,  0) AS UNIT_WEIGHT
        FROM PS_DISPATCH_D d
        LEFT JOIN KNRAWMS.RECDI rd ON rd.SKUKEY = d.SKUKEY
        WHERE d.DISPATCH_NO = :dispatchNo
        ORDER BY d.SEQ
        """, nativeQuery = true)
    List<Object[]> findItemsWithUnitWeight(@Param("dispatchNo") String dispatchNo);
}
