package com.company.module.shipment.service;

import com.company.module.shipment.dto.*;
import com.company.module.shipment.repository.ShpdHRepository;
import com.company.module.shipment.repository.ShpdIRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 출고진행현황 서비스
 *
 * Flask 대응:
 *   - api_shipment_schedule (POST /api/shipment/schedule)
 *   - api_shipment_filter_opts (GET /api/shipment/schedule/filter-opts)
 *
 * PLT_CNT 계산 방식 (SKUMA.GRSWGT 기반, 파렛트 1,200 kg 기준):
 *   - 원지(H prefix): CEIL(QTSHPO(kg) / 1200)
 *   - 판지(F/S prefix): CEIL(QTSHPO(속) × GRSWGT(kg/속) / 1200)
 *   - GRSWGT 미등록: null (표시 불가)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "wmsTransactionManager")
public class ShipmentService {

    private static final double PLT_CAP_KG = 1200.0;

    private final ShpdHRepository shpdHRepository;
    private final ShpdIRepository shpdIRepository;

    @PersistenceContext(unitName = "wmsPU")
    private EntityManager em;

    // ──────────────────────────────────────────────────────────────────────────
    // 출고진행현황 조회
    // ──────────────────────────────────────────────────────────────────────────

    public Map<String, Object> getSchedule(ShipmentSearchRequest req) {

        // ── 동적 WHERE 구성 ───────────────────────────────────────────────────
        StringBuilder whereSb = new StringBuilder("1=1");
        List<Object> params   = new ArrayList<>();

        if (hasText(req.getWareky())) {
            whereSb.append(" AND SH.WAREKY = ?");
            params.add(req.getWareky());
        }
        String df = req.normalizedDateFrom();
        String dt = req.normalizedDateTo();
        if (hasText(df)) { whereSb.append(" AND SH.RQSHPD >= ?"); params.add(df); }
        if (hasText(dt)) { whereSb.append(" AND SH.RQSHPD <= ?"); params.add(dt); }

        if (hasText(req.getStatdo())) {
            whereSb.append(" AND SH.STATDO = ?");
            params.add(req.getStatdo());
        }
        if (hasText(req.getSkug05())) {
            whereSb.append(" AND TRIM(SI.SKUG05) = ?");
            params.add(req.getSkug05().strip());
        }
        if (req.getLota02List() != null && !req.getLota02List().isEmpty()) {
            String ph = req.getLota02List().stream().map(x -> "?").collect(Collectors.joining(","));
            whereSb.append(" AND TRIM(SI.LOTA02) IN (").append(ph).append(")");
            params.addAll(req.getLota02List());
        }
        if (hasText(req.getKeyword())) {
            whereSb.append(" AND (SI.SKUKEY LIKE ? OR SI.DESC01 LIKE ?)");
            params.add("%" + req.getKeyword() + "%");
            params.add("%" + req.getKeyword() + "%");
        }

        String whereSQL = whereSb.toString();

        // ── 전체 건수 ─────────────────────────────────────────────────────────
        String countSQL = """
            SELECT COUNT(*)
            FROM KNRAWMS.SHPDI SI
            INNER JOIN KNRAWMS.SHPDH SH ON SH.SHPOKY = SI.SHPOKY
            LEFT  JOIN KNRAWMS.SKUMA M  ON SI.SKUKEY = M.SKUKEY AND SH.OWNRKY = M.OWNRKY
            WHERE """ + whereSQL;

        var countQuery = em.createNativeQuery(countSQL);
        for (int i = 0; i < params.size(); i++) countQuery.setParameter(i + 1, params.get(i));
        long total = ((Number) countQuery.getSingleResult()).longValue();

        // ── 본문 쿼리 ─────────────────────────────────────────────────────────
        String baseSQL = """
            SELECT
                SI.SHPOKY, SI.SHPOIT, SI.SKUKEY, SI.DESC01,
                TRIM(COALESCE(SI.SKUG05,''))  AS SKUG05,
                CD.CDESC1                     AS SKUG05NM,
                SI.UOMKEY,
                CAST(COALESCE(SI.QTSHPO,0) AS DECIMAL(18,4)) AS QTSHPO,
                CAST(COALESCE(SI.QTUALO,0) AS DECIMAL(18,4)) AS QTUALO,
                CAST(COALESCE(SI.QTALOC,0) AS DECIMAL(18,4)) AS QTALOC,
                CAST(COALESCE(SI.QTJCMP,0) AS DECIMAL(18,4)) AS QTJCMP,
                CAST(COALESCE(SI.QTSHPD,0) AS DECIMAL(18,4)) AS QTSHPD,
                TRIM(COALESCE(SI.STATIT,''))  AS STATIT,
                TRIM(COALESCE(SI.STDLNR,''))  AS STDLNR,
                TRIM(COALESCE(SI.SVBELN,''))  AS SVBELN,
                TRIM(COALESCE(SI.LOTA01,''))  AS LOTA01,
                TRIM(COALESCE(SI.LOTA02,''))  AS LOTA02,
                TRIM(COALESCE(SI.LOTA03,''))  AS LOTA03,
                TRIM(COALESCE(SI.TLOTA01,'')) AS TLOTA01,
                TRIM(COALESCE(SI.TLOTA02,'')) AS TLOTA02,
                COALESCE((SELECT CDESC1 FROM KNRAWMS.CMCDV WHERE CMCDKY='LOTA02' AND CMCDVL=SI.LOTA02),'')  AS LOTA02NM,
                COALESCE((SELECT CDESC1 FROM KNRAWMS.CMCDV WHERE CMCDKY='LOTA02' AND CMCDVL=SI.TLOTA02),'') AS TLOTA02NM,
                SI.CREDAT, SI.CRETIM, SI.CREUSR,
                SI.LMODAT, SI.LMOTIM, SI.LMOUSR,
                SI.ALSTKY,
                SH.PRTCHK,
                SH.WAREKY,
                SH.OWNRKY,
                SH.DPTNKY,
                COALESCE((SELECT NAME01 FROM KNRAWMS.BZPTN WHERE OWNRKY=SH.OWNRKY AND PTNRTY='CT' AND PTNRKY=SH.DPTNKY),'') AS DPTNM,
                COALESCE((SELECT NAME01 FROM KNRAWMS.BZPTN WHERE OWNRKY=SH.OWNRKY AND PTNRTY='VD' AND PTNRKY=SH.PTRCVR),'') AS PTRCVRNM,
                SH.RQSHPD, SH.DOCDAT,
                SH.STATDO,
                COALESCE(ST.CDESC1,'')        AS STATDONM,
                SH.SHPMTY,
                COALESCE((SELECT CDESC1 FROM KNRAWMS.CMCDV WHERE CMCDKY='TASOTY' AND CMCDVL=SH.SHPMTY),'') AS SHPMTYNM,
                SH.DOCUTY                     AS DOCUTYNM,
                SH.VEHINO,
                SI.MEASKY,
                CASE WHEN TRIM(SI.SKUG05)='10' AND M.GRSWGT IS NOT NULL AND M.GRSWGT > 0
                     THEN M.GRSWGT
                     ELSE NULL
                END                           AS PLTKG,
                COALESCE(
                    (SELECT ME.QTAUOM FROM KNRAWMS.MEASI ME
                     WHERE ME.WAREKY=SH.WAREKY AND ME.MEASKY=SI.MEASKY
                       AND TRIM(ME.UOMKEY)='SOK'
                     FETCH FIRST 1 ROWS ONLY), NULL)          AS SOK_PER_R,
                COALESCE(
                    (SELECT ME2.QTAUOM FROM KNRAWMS.MEASI ME2
                     WHERE ME2.WAREKY=SH.WAREKY AND ME2.MEASKY=SI.MEASKY
                       AND TRIM(ME2.UOMKEY)='KG'
                     FETCH FIRST 1 ROWS ONLY), 0)             AS KG_PER_UNIT,
                COALESCE(
                    (SELECT ME3.QTAUOM FROM KNRAWMS.MEASI ME3
                     WHERE ME3.WAREKY=SH.WAREKY AND ME3.MEASKY=SI.MEASKY
                       AND TRIM(ME3.UOMKEY)='BAG'
                     FETCH FIRST 1 ROWS ONLY), 0)             AS BAG_PER_UNIT,
                COALESCE(
                    (SELECT ME4.QTAUOM FROM KNRAWMS.MEASI ME4
                     WHERE ME4.WAREKY=SH.WAREKY AND ME4.MEASKY=SI.MEASKY
                       AND TRIM(ME4.UOMKEY)='BOX'
                     FETCH FIRST 1 ROWS ONLY), 0)             AS BOX_PER_UNIT,
                COALESCE(
                    (SELECT ME5.QTAUOM FROM KNRAWMS.MEASI ME5
                     WHERE ME5.WAREKY=SH.WAREKY AND ME5.MEASKY=SI.MEASKY
                       AND TRIM(ME5.UOMKEY)='PAL'
                     FETCH FIRST 1 ROWS ONLY), 0)             AS PAL_PER_UNIT,
                COALESCE(
                    (SELECT ME6.QTAUOM FROM KNRAWMS.MEASI ME6
                     WHERE ME6.WAREKY=SH.WAREKY AND ME6.MEASKY=SI.MEASKY
                       AND TRIM(ME6.UOMKEY)='EA'
                     FETCH FIRST 1 ROWS ONLY), 0)             AS EA_PER_UNIT
            FROM KNRAWMS.SHPDI SI
            INNER JOIN KNRAWMS.SHPDH SH ON SH.SHPOKY = SI.SHPOKY
            LEFT  JOIN KNRAWMS.BZPTN CT ON CT.OWNRKY=SH.OWNRKY AND CT.PTNRTY='CT' AND CT.PTNRKY=SH.DPTNKY
            LEFT  JOIN KNRAWMS.BZPTN VD ON VD.OWNRKY=SH.OWNRKY AND VD.PTNRTY='VD' AND VD.PTNRKY=SH.PTRCVR
            LEFT  JOIN KNRAWMS.CMCDV ST ON ST.CMCDKY='STATDO' AND ST.CMCDVL=SH.STATDO
            LEFT  JOIN KNRAWMS.CMCDV CD ON CD.CMCDKY='SKUG05' AND CD.CMCDVL=SI.SKUG05
            LEFT  JOIN KNRAWMS.SKUMA M  ON SI.SKUKEY=M.SKUKEY AND SH.OWNRKY=M.OWNRKY
            WHERE """ + whereSQL + """
            ORDER BY SI.SVBELN, SI.SHPOKY, SI.SHPOIT
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """;

        int size   = Math.max(1, Math.min(req.getSize(), 500));
        int offset = req.getPage() * size;

        var dataQuery = em.createNativeQuery(baseSQL);
        for (int i = 0; i < params.size(); i++) dataQuery.setParameter(i + 1, params.get(i));
        dataQuery.setParameter(params.size() + 1, offset);  // OFFSET ? ROWS
        dataQuery.setParameter(params.size() + 2, size);    // FETCH NEXT ? ROWS ONLY

        @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();

        List<ShipmentRowResponse> result = new ArrayList<>();
        for (Object[] r : rows) {
            result.add(mapRow(r));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", total);
        response.put("page",  req.getPage());
        response.put("size",  size);
        response.put("rows",  result);
        return response;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 필터 옵션 조회
    // ──────────────────────────────────────────────────────────────────────────

    public ShipmentFilterOptsResponse getFilterOpts() {
        // 창고 목록
        List<String> warekyList = shpdHRepository.findDistinctWareky();

        // 출고상태 목록
        @SuppressWarnings("unchecked")
        List<Object[]> statitRows = em.createNativeQuery(
            "SELECT CMCDVL AS value, CDESC1 AS label FROM KNRAWMS.CMCDV WHERE CMCDKY='STATIT' ORDER BY CMCDVL"
        ).getResultList();
        List<ShipmentFilterOptsResponse.CodeLabel> statitList = statitRows.stream()
            .map(row -> ShipmentFilterOptsResponse.CodeLabel.builder()
                .value(str(row[0])).label(str(row[1])).build())
            .collect(Collectors.toList());

        // SKUG05 목록
        @SuppressWarnings("unchecked")
        List<Object[]> skug05Rows = em.createNativeQuery(
            "SELECT CMCDVL AS value, CDESC1 AS label FROM KNRAWMS.CMCDV WHERE CMCDKY='SKUG05' ORDER BY CMCDVL"
        ).getResultList();
        List<ShipmentFilterOptsResponse.CodeLabel> skug05List = skug05Rows.stream()
            .map(row -> ShipmentFilterOptsResponse.CodeLabel.builder()
                .value(str(row[0])).label(str(row[1])).build())
            .collect(Collectors.toList());

        // LOTA02(플랜트) 목록 — 공백 제거
        List<String> lota02List = shpdIRepository.findDistinctLota02();

        // 최대 납품요청일
        String maxDate = shpdHRepository.findMaxRqshpd().orElse("");

        return ShipmentFilterOptsResponse.builder()
            .warekyList(warekyList)
            .statitList(statitList)
            .skug05List(skug05List)
            .lota02List(lota02List)
            .maxDate(maxDate)
            .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 행 매핑 (Object[] → ShipmentRowResponse)
    // ──────────────────────────────────────────────────────────────────────────

    private ShipmentRowResponse mapRow(Object[] r) {
        // 컬럼 인덱스 (SELECT 순서 기준)
        // 0:SHPOKY 1:SHPOIT 2:SKUKEY 3:DESC01 4:SKUG05 5:SKUG05NM 6:UOMKEY
        // 7:QTSHPO 8:QTUALO 9:QTALOC 10:QTJCMP 11:QTSHPD
        // 12:STATIT 13:STDLNR 14:SVBELN 15:LOTA01 16:LOTA02 17:LOTA03
        // 18:TLOTA01 19:TLOTA02 20:LOTA02NM 21:TLOTA02NM
        // 22:CREDAT 23:CRETIM 24:CREUSR 25:LMODAT 26:LMOTIM 27:LMOUSR 28:ALSTKY
        // 29:PRTCHK 30:WAREKY 31:OWNRKY 32:DPTNKY 33:DPTNM 34:PTRCVRNM
        // 35:RQSHPD 36:DOCDAT 37:STATDO 38:STATDONM 39:SHPMTY 40:SHPMTYNM
        // 41:DOCUTYNM 42:VEHINO 43:MEASKY
        // 44:PLTKG 45:SOK_PER_R 46:KG_PER_UNIT 47:BAG_PER_UNIT 48:BOX_PER_UNIT 49:PAL_PER_UNIT 50:EA_PER_UNIT

        String  skug05  = str(r[4]);
        String  uomkey  = str(r[6]).strip();
        double  qtshpo  = toDouble(r[7]);
        double  kgPer   = toDouble(r[46]);   // MEASI KG 환산
        double  bagPer  = toDouble(r[47]);
        double  boxPer  = toDouble(r[48]);
        double  palPer  = toDouble(r[49]);
        double  eaPer   = toDouble(r[50]);

        // 중량 환산 (단위별)
        double  kgVal   = kgPer  > 0 ? round4(qtshpo * kgPer)  : ("KG".equals(uomkey) ? qtshpo : 0.0);
        double  bagVal  = bagPer > 0 ? round4(qtshpo / bagPer) : 0.0;
        double  boxVal  = boxPer > 0 ? round4(qtshpo / boxPer) : 0.0;
        double  palVal  = palPer > 0 ? round4(qtshpo / palPer) : 0.0;
        double  sokVal  = toDouble(r[45]) > 0 ? round4(qtshpo * toDouble(r[45])) : 0.0;
        double  eaVal   = eaPer  > 0 ? round4(qtshpo * eaPer)  : 0.0;

        // BOX/BAG 구분
        String  boxbag  = boxPer > 0 ? "BOX" : (bagPer > 0 ? "BAG" : "");
        double  boxbagVal = boxPer > 0 ? boxVal : bagVal;

        // PLT개수 계산 (SKUMA.GRSWGT 기반)
        Integer pltCnt  = calcPltCnt(str(r[2]), skug05, qtshpo, toDoubleNullable(r[44]));

        // SOK_PER_R
        Double sokPerR = r[45] != null ? toDouble(r[45]) : null;

        // 수량 BOX 환산 헬퍼
        double qtualo  = toDouble(r[8]);
        double qtaloc  = toDouble(r[9]);
        double qtjcmp  = toDouble(r[10]);
        double qtshpd  = toDouble(r[11]);
        double bx = boxPer > 0 ? boxPer : (bagPer > 0 ? bagPer : 0);

        return ShipmentRowResponse.builder()
            .shpoky(str(r[0])).shpoit(str(r[1]))
            .skukey(str(r[2])).desc01(str(r[3]))
            .skug05(skug05).skug05Nm(str(r[5]))
            .uomkey(uomkey)
            .qtshpo(qtshpo)
            .tot(kgVal)
            .bag(bagVal).box(boxVal).plt(palVal).sok(sokVal).ea(eaVal)
            .boxbag(boxbag)
            .qtualo(qtualo).qtaloc(qtaloc).qtjcmp(qtjcmp).qtshpd(qtshpd)
            .qtualoBox(bx > 0 ? round4(qtualo / bx) : 0.0)
            .qtalocBox(bx > 0 ? round4(qtaloc / bx) : 0.0)
            .qtjcmpBox(bx > 0 ? round4(qtjcmp / bx) : 0.0)
            .qtshpdBox(bx > 0 ? round4(qtshpd / bx) : 0.0)
            .statit(str(r[12])).stdlnr(str(r[13])).svbeln(str(r[14]))
            .lota01(str(r[15])).lota02(str(r[16])).lota03(str(r[17]))
            .tlota01(str(r[18])).tlota02(str(r[19]))
            .lota02Nm(str(r[20])).tlota02Nm(str(r[21]))
            .credat(str(r[22])).cretim(str(r[23])).creusr(str(r[24]))
            .lmodat(str(r[25])).lmotim(str(r[26])).lmousr(str(r[27]))
            .prtchk(str(r[29]))
            .wareky(str(r[30])).dptnky(str(r[32]))
            .dptnm(str(r[33])).ptrcvrNm(str(r[34]))
            .rqshpd(str(r[35])).docdat(str(r[36]))
            .statdo(str(r[37])).statdoNm(str(r[38]))
            .shpmty(str(r[39])).shpmtyNm(str(r[40]))
            .docutynm(str(r[41])).vehino(str(r[42]))
            .measky(str(r[43]))
            .pltCnt(pltCnt).sokPerR(sokPerR)
            .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PLT개수 계산
    //   원지(H prefix): CEIL(QTSHPO(kg) / 1200)
    //   판지(F/S prefix): CEIL(QTSHPO(속) × GRSWGT(kg/속) / 1200)
    //   GRSWGT 미등록: null
    // ──────────────────────────────────────────────────────────────────────────
    private Integer calcPltCnt(String skukey, String skug05, double qtshpo, Double grswgt) {
        if (!"10".equals(skug05.strip())) return null;
        if (qtshpo <= 0) return null;

        String prefix = skukey == null || skukey.isEmpty() ? "" : skukey.substring(0, 1).toUpperCase();

        if ("H".equals(prefix)) {
            // 원지: QTSHPO 자체가 kg 단위
            return (int) Math.ceil(qtshpo / PLT_CAP_KG);
        } else if (grswgt != null && grswgt > 0) {
            // 판지(F/S/기타): QTSHPO(속) × GRSWGT(kg/속) / 파렛트용량
            double totalKg = qtshpo * grswgt;
            return (int) Math.ceil(totalKg / PLT_CAP_KG);
        }
        return null;  // GRSWGT 미등록 → 계산 불가
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 내부 유틸
    // ──────────────────────────────────────────────────────────────────────────
    private boolean hasText(String s) { return s != null && !s.strip().isEmpty(); }

    private String str(Object o) { return o == null ? "" : o.toString().strip(); }

    private double toDouble(Object o) {
        if (o == null) return 0.0;
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return 0.0; }
    }

    private Double toDoubleNullable(Object o) {
        if (o == null) return null;
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return null; }
    }

    private double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
