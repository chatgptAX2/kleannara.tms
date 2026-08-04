package com.company.module.dispatch.service;

import com.company.module.dispatch.dto.PsReturnDocResponse;
import com.company.module.dispatch.dto.PsReturnSaveRequest;
import com.company.module.dispatch.dto.PsReturnSearchRequest;
import com.company.module.dispatch.repository.PsDispatchHRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 반품 배차 서비스 (신규)
 * 원천: KNRAWMS.IFWMS103 (반품입고 BWART=131) + SKUMA + BZPTN
 *
 * ※ 기존 PsDispatchService 로직은 전혀 건드리지 않는다.
 *   - 조회: searchReturnDocs()   → 배차(반품) 탭 / 반품출고 메뉴 대상 리스트
 *   - 저장: saveReturnDispatch() → DISPATCH_TYPE='GR' + IFWMS103.STKNUM(가선적번호) UPDATE
 *
 * ■ DataSource 라우팅
 *   - em     (wmsPU, Oracle KNRAWMS): IFWMS103, SKUMA, BZPTN, SZF_GET_CONVERT_QTY
 *   - tmsEm  (tmsPU, Oracle KNRAWMS): PS_DISPATCH_H, PS_DISPATCH_D
 *   ※ IFWMS103/SKUMA/BZPTN/PS_DISPATCH_* 는 모두 동일 KNRAWMS DB.
 *     저장 트랜잭션은 tmsTransactionManager 이므로 IFWMS103 UPDATE 도 tmsEm 으로 실행하여
 *     단일 트랜잭션 일관성을 확보한다(조회는 readOnly 로 em 사용).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "tmsTransactionManager")
public class PsReturnService {

    /** 배차번호 채번 재사용 (MariaDB PS_DISPATCH_H prefix 최대값) */
    private final PsDispatchHRepository dispatchHRepo;

    /** Oracle WMS — KNRAWMS.IFWMS103 / SKUMA / BZPTN (SZF_GET_CONVERT_QTY 함수 포함) */
    @PersistenceContext(unitName = "wmsPU")
    private EntityManager em;

    /** Oracle KNRAWMS TMS — PS_DISPATCH_H / PS_DISPATCH_D (+ 저장 트랜잭션 내 IFWMS103 UPDATE) */
    @PersistenceContext(unitName = "tmsPU")
    private EntityManager tmsEm;

    // 입고예정일 검색 허용 최대범위: 오늘 기준 -30 ~ +7 (요구사양)
    private static final int MAX_BACK_DAYS = 30;
    private static final int MAX_FWD_DAYS  = 7;

    /**
     * 반품 배차 대상 납품문서 조회.
     * 필수: wareky(거점), skug05(제품군), 입고예정일 범위
     * 선택: lifnr(납품처코드), ebeln(납품문서번호)
     */
    public List<PsReturnDocResponse> searchReturnDocs(PsReturnSearchRequest req) {
        // ── 입고예정일 범위 정규화 + 허용범위 클램프 ─────────────
        LocalDate today = LocalDate.now();
        LocalDate minDate = today.minusDays(MAX_BACK_DAYS);
        LocalDate maxDate = today.plusDays(MAX_FWD_DAYS);

        LocalDate from = parseDateOr(req.getDateFrom(), today.minusDays(3));
        LocalDate to   = parseDateOr(req.getDateTo(),   today.plusDays(3));
        if (from.isBefore(minDate)) from = minDate;
        if (to.isAfter(maxDate))    to   = maxDate;
        if (to.isBefore(from))      to   = from;

        String eindtFrom = from.format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
        String eindtTo   = to.format(DateTimeFormatter.BASIC_ISO_DATE);

        String wareky = isBlank(req.getWareky()) ? "1100" : req.getWareky().strip();
        String skug05 = req.getSkug05() == null ? "" : req.getSkug05().strip();
        String lifnr  = req.getLifnr() == null ? "" : req.getLifnr().strip();
        String ebeln  = req.getEbeln() == null ? "" : req.getEbeln().strip();

        // SZF_GET_CONVERT_QTY 환산에 사용할 실수량 표현식 (요구 쿼리 그대로)
        String qtyExpr = "(CASE WHEN PO.IFFLG != 'N' THEN PO.MENGE_R "
                       + "ELSE (PO.MENGE - PO.MENGE_R) END)";
        String conv = "NVL(TRIM(SZF_GET_CONVERT_QTY('1100', PO.SKUKEY, " + qtyExpr + ", PO.MEINS, '%s')),'0.000')";

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT PO.SEQNO, PO.EBELN, PO.EBELP, PO.WAREKY, PO.BWART, PO.EINDT, ")
           .append("PO.LIFNR, BZ.NAME01 AS DPTNNM, PO.PTNRTY, PO.BEDAT, PO.IFID, PO.BWARTSAP, ")
           .append("PO.DLEFLG, PO.SKUKEY, SM.DESC01, SM.SKUG05, PO.MEINS, ")
           .append("PO.LOTA01, PO.LOTA02, PO.LOTA14, PO.LOTA15, PO.FLOTA01, PO.FLOTA02, PO.STKNUM, ")
           .append(String.format(conv, "KG")).append(" AS TOT, ")
           .append(String.format(conv, "BAG")).append(" AS BAG, ")
           .append(String.format(conv, "BOX")).append(" AS BOX, ")
           .append(String.format(conv, "PAL")).append(" AS PLT, ")
           .append(String.format(conv, "SOK")).append(" AS SOK, ")
           .append(String.format(conv, "EA")).append(" AS EA, ")
           .append("CASE WHEN ").append(qtyExpr).append(" = 0 THEN 0 ")
           .append("ELSE NVL((TRIM(SZF_GET_CONVERT_QTY('1100', PO.SKUKEY, ").append(qtyExpr).append(", PO.MEINS, 'BAG')) / ")
           .append("TRIM(SZF_GET_CONVERT_QTY('1100', PO.SKUKEY, ").append(qtyExpr).append(", PO.MEINS, 'BOX'))),'0.000') END AS BOXBAG ")
           .append("FROM KNRAWMS.IFWMS103 PO ")
           .append("LEFT OUTER JOIN KNRAWMS.SKUMA SM ON SM.OWNRKY = 'KN' AND SM.SKUKEY = PO.SKUKEY ")
           .append("LEFT OUTER JOIN KNRAWMS.BZPTN BZ ON BZ.PTNRKY = PO.LIFNR AND BZ.OWNRKY = 'KN' AND BZ.PTNRTY = PO.PTNRTY ")
           // ── 고정조건 ──
           .append("WHERE PO.IFID IN ('TMS_IFC206', 'TMS_IFB206', 'TMS_IFS206') ")
           .append("AND PO.STATUS != 'D' ")
           // ── 필수조건 ──
           .append("AND SM.SKUG05 = ? ")     // 1 제품군
           .append("AND PO.WAREKY = ? ")     // 2 거점
           .append("AND PO.EINDT >= ? ")     // 3 입고예정일 FROM
           .append("AND PO.EINDT <= ? ");    // 4 입고예정일 TO

        List<Object> params = new ArrayList<>();
        params.add(skug05);
        params.add(wareky);
        params.add(eindtFrom);
        params.add(eindtTo);

        // ── 선택조건 ──
        if (!lifnr.isEmpty()) { sql.append("AND PO.LIFNR = ? "); params.add(lifnr); }
        if (!ebeln.isEmpty()) { sql.append("AND PO.EBELN = ? "); params.add(ebeln); }

        sql.append("ORDER BY PO.EINDT, PO.LIFNR, PO.EBELN, PO.EBELP");

        var query = em.createNativeQuery(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<PsReturnDocResponse> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            String stknum = str(r[23]);
            boolean dispatched = !isBlank(stknum);  // STKNUM != ' ' 이면서 값 존재 → 배차완료
            out.add(PsReturnDocResponse.builder()
                .seqno(str(r[0]))
                .ebeln(str(r[1]))
                .ebelp(str(r[2]))
                .wareky(str(r[3]))
                .bwart(str(r[4]))
                .eindt(str(r[5]))
                .lifnr(str(r[6]))
                .dptnnm(str(r[7]))
                .ptnrty(str(r[8]))
                .bedat(str(r[9]))
                .ifid(str(r[10]))
                .bwartsap(str(r[11]))
                .dleflg(str(r[12]))
                .skukey(str(r[13]))
                .desc01(str(r[14]))
                .skug05(str(r[15]))
                .meins(str(r[16]))
                .lota01(str(r[17]))
                .lota02(str(r[18]))
                .lota14(str(r[19]))
                .lota15(str(r[20]))
                .flota01(str(r[21]))
                .flota02(str(r[22]))
                .stknum(stknum)
                .tot(str(r[24]))
                .bag(str(r[25]))
                .box(str(r[26]))
                .plt(str(r[27]))
                .sok(str(r[28]))
                .ea(str(r[29]))
                .boxbag(str(r[30]))
                .dispatched(dispatched)
                .build());
        }
        return out;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 반품 배차 저장
    //  1) PS_DISPATCH_H INSERT (DISPATCH_TYPE='GR', STATUS='DRAFT')  → tmsEm
    //  2) PS_DISPATCH_D INSERT (SHPOKY=EBELN, SHPOIT=EBELP 로 매핑)  → tmsEm
    //  3) UPDATE IFWMS103 SET STKNUM=가선적번호(DISPATCH_NO)          → tmsEm
    //       WHERE EBELN=? AND EBELP=? AND STATUS != 'D'  (저장 시점 실행)
    //  ※ 가선적번호 = DISPATCH_NO (PS배차 채번 규칙과 동일)
    // ──────────────────────────────────────────────────────────────────────────
    @Transactional(transactionManager = "tmsTransactionManager")
    public List<String> saveReturnDispatch(PsReturnSaveRequest req) {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
        List<String> saved = new ArrayList<>();

        for (PsReturnSaveRequest.VehicleBlock veh : req.getVehicles()) {
            String dt         = isBlank(veh.getRqshpd()) ? today : veh.getRqshpd().replace("-", "");
            String dispatchNo = nextDispatchNo(dt);   // = 가선적번호
            String carclassCd = veh.getCarclassCd() == null ? "" : veh.getCarclassCd().strip();
            double totalKg    = veh.getTotalKg() == null ? 0.0 : veh.getTotalKg();
            int    totalCnt   = veh.getItems() == null ? 0 : veh.getItems().size();

            // 1) PS_DISPATCH_H INSERT (DISPATCH_TYPE='GR')
            tmsEm.createNativeQuery("""
                INSERT INTO KNRAWMS.PS_DISPATCH_H
                  (DISPATCH_NO, DISPATCH_DT, RQSHPD, DPTNKY, DPTNM,
                   CARTYPE, STATUS, TOTAL_KG, TOTAL_CNT, CREDAT, CREUSR, DISPATCH_TYPE)
                VALUES (?,?,?,?,?,?,'DRAFT',?,?,?,?,'GR')
                """)
              .setParameter(1,  dispatchNo)
              .setParameter(2,  today)
              .setParameter(3,  dt)
              .setParameter(4,  veh.getLifnr())
              .setParameter(5,  veh.getDptnnm())
              .setParameter(6,  veh.getCartype())
              .setParameter(7,  totalKg)
              .setParameter(8,  totalCnt)
              .setParameter(9,  today)
              .setParameter(10, "SYSTEM")
              .executeUpdate();

            List<PsReturnSaveRequest.ItemBlock> items = veh.getItems();
            List<String[]> keys = new ArrayList<>();   // {EBELN, EBELP}

            if (items != null) {
                int seq = 1;
                for (PsReturnSaveRequest.ItemBlock it : items) {
                    // 반품 문서 키: EBELN(납품문서번호)/EBELP(아이템)
                    //  PS_DISPATCH_D.SHPOKY/SHPOIT 는 NOT NULL → EBELN/EBELP 로 매핑(호환).
                    String ebeln = it.getEbeln() == null ? "" : it.getEbeln().strip();
                    String ebelp = it.getEbelp() == null ? "" : it.getEbelp().strip();
                    if (isBlank(ebeln) || isBlank(ebelp)) {
                        throw new IllegalArgumentException(
                            "반품 배차 항목에 납품문서번호(EBELN) 또는 아이템(EBELP)이 없습니다. " +
                            "[차량=" + dispatchNo + ", SKUKEY=" + str(it.getSkukey()) +
                            ", EBELN=" + str(it.getEbeln()) + ", EBELP=" + str(it.getEbelp()) + "]");
                    }

                    // 2) PS_DISPATCH_D INSERT (SHPOKY=EBELN, SHPOIT=EBELP)
                    tmsEm.createNativeQuery("""
                        INSERT INTO KNRAWMS.PS_DISPATCH_D
                          (DISPATCH_NO,SEQ,SHPOKY,SHPOIT,SKUKEY,DESC01,
                           QTSHPO,UOMKEY,DPTNKY,DPTNM,IS_SPLIT,ORG_SHPOKY,ORG_SHPOIT,
                           GRSWGT,KG_WEIGHT)
                        VALUES (?,?,?,?,?,?,?,?,?,?,0,?,?,?,?)
                        """)
                      .setParameter(1,  dispatchNo)
                      .setParameter(2,  seq++)
                      .setParameter(3,  ebeln)
                      .setParameter(4,  ebelp)
                      .setParameter(5,  it.getSkukey())
                      .setParameter(6,  it.getDesc01())
                      .setParameter(7,  it.getQtshpo() == null ? 0.0 : it.getQtshpo())
                      .setParameter(8,  it.getUomkey() == null ? "KG" : it.getUomkey())
                      .setParameter(9,  it.getDptnky())
                      .setParameter(10, it.getDptnm())
                      .setParameter(11, ebeln)   // ORG_SHPOKY = EBELN
                      .setParameter(12, ebelp)   // ORG_SHPOIT = EBELP
                      .setParameter(13, it.getGrswgt() == null ? 0.0 : it.getGrswgt())
                      .setParameter(14, it.getKgWeight() == null ? 0.0 : it.getKgWeight())
                      .executeUpdate();

                    keys.add(new String[]{ebeln, ebelp});
                }
            }

            // 3) UPDATE IFWMS103 SET STKNUM = 가선적번호(DISPATCH_NO)
            //    WHERE EBELN=? AND EBELP=? AND STATUS != 'D'  (저장 시점 실행)
            //    ※ IFWMS103 도 KNRAWMS 이므로 활성 트랜잭션(tmsEm)으로 갱신하여 단일 트랜잭션 유지.
            for (String[] k : keys) {
                tmsEm.createNativeQuery("""
                    UPDATE KNRAWMS.IFWMS103
                    SET STKNUM = ?
                    WHERE EBELN = ? AND EBELP = ? AND STATUS != 'D'
                    """)
                  .setParameter(1, dispatchNo)
                  .setParameter(2, k[0])
                  .setParameter(3, k[1])
                  .executeUpdate();
            }

            log.info("[PsReturn] 반품 배차 저장 완료 dispatchNo={}(가선적), 품목={}건, 차종={}",
                     dispatchNo, keys.size(), carclassCd);
            saved.add(dispatchNo);
        }
        return saved;
    }

    /**
     * 배차번호 채번 (PS배차 nextDispatchNo 규칙과 동일: yymmdd + 3자리 + 'T').
     * MariaDB PS_DISPATCH_H 의 동일 prefix 최대값 기준 +1.
     */
    @Transactional(transactionManager = "tmsTransactionManager")
    public String nextDispatchNo(String yyyymmdd) {
        String dt = yyyymmdd == null ? "" : yyyymmdd.replace("-", "");
        String yymmdd = dt.length() == 8 ? dt.substring(2)
                      : (dt.length() >= 6 ? dt.substring(dt.length() - 6) : dt);
        String prefix = yymmdd;
        Optional<String> maxOpt = dispatchHRepo.findMaxDispatchNoByPrefix(prefix);
        int seq = 1;
        if (maxOpt.isPresent() && maxOpt.get() != null && maxOpt.get().length() == 10) {
            try { seq = Integer.parseInt(maxOpt.get().substring(6, 9)) + 1; }
            catch (Exception ignored) {}
        }
        return String.format("%s%03dT", yymmdd, seq);
    }

    // ── 헬퍼 ───────────────────────────────────────────────────
    private LocalDate parseDateOr(String s, LocalDate def) {
        if (s == null || s.isBlank()) return def;
        String v = s.strip().replace("-", "").replace("/", "");
        try {
            if (v.length() == 8) return LocalDate.parse(v, DateTimeFormatter.BASIC_ISO_DATE);
            return LocalDate.parse(s.strip());
        } catch (Exception e) {
            return def;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.strip().isEmpty();
    }

    private String str(Object o) {
        return o == null ? "" : o.toString().strip();
    }
}
