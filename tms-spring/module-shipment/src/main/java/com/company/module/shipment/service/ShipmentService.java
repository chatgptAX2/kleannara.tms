package com.company.module.shipment.service;

import com.company.module.shipment.dto.*;
import com.company.module.shipment.repository.ShpdHRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
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

    @PersistenceContext(unitName = "wmsPU")
    private EntityManager em;

    // ──────────────────────────────────────────────────────────────────────────
    // 출고진행현황 조회
    //
    // ■ 소프트파싱(Soft Parsing) 설계
    //   고정 SQL 텍스트 + (? IS NULL OR col ...) 패턴 사용
    //   → 동일 파라미터 조합 재호출 시 Oracle Shared Pool 히트 → 소프트파싱
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 출고현황 COUNT SQL (고정 텍스트)
     * p1,p2: wareky   (? IS NULL OR SH.WAREKY = ?)
     * p3,p4: dateFrom (? IS NULL OR SH.RQSHPD >= ?)
     * p5,p6: dateTo   (? IS NULL OR SH.RQSHPD <= ?)
     * p7,p8: statdo   (? IS NULL OR SH.STATDO = ?)
     * p9,p10: skug05  (? IS NULL OR SI.SKUG05 = ?)
     * p11,p12: lota02 (? IS NULL OR INSTR(',' || ? || ',', ',' || SI.LOTA02 || ',') > 0)
     * p13,p14,p15: keyword (? IS NULL OR SI.SKUKEY LIKE ? OR SI.DESC01 LIKE ?)
     * p16,p17: ptnrky (? IS NULL OR SH.DPTNKY = ?)
     * p18,p19: svbeln (? IS NULL OR INSTR(',' || ? || ',', ',' || SI.SVBELN || ',') > 0)
     */
    private static final String SCHEDULE_COUNT_SQL =
        "SELECT COUNT(*)" +
        " FROM KNRAWMS.SHPDI SI" +
        " INNER JOIN KNRAWMS.SHPDH SH ON SH.SHPOKY = SI.SHPOKY" +
        " LEFT  JOIN KNRAWMS.SKUMA M  ON SI.SKUKEY = M.SKUKEY AND SH.OWNRKY = M.OWNRKY" +
        " WHERE (? IS NULL OR SH.WAREKY = ?)" +
        "   AND (? IS NULL OR SH.RQSHPD >= ?)" +
        "   AND (? IS NULL OR SH.RQSHPD <= ?)" +
        "   AND (? IS NULL OR SH.STATDO = ?)" +
        "   AND (? IS NULL OR SI.SKUG05 = ?)" +
        "   AND (? IS NULL OR INSTR(',' || ? || ',', ',' || SI.LOTA02 || ',') > 0)" +
        "   AND (? IS NULL OR SI.SKUKEY LIKE ? OR SI.DESC01 LIKE ?)" +
        "   AND (? IS NULL OR SH.DPTNKY = ?)" +
        "   AND (? IS NULL OR INSTR(',' || ? || ',', ',' || SI.SVBELN || ',') > 0)";

    /**
     * 출고현황 본문 SQL (고정 텍스트, 동일한 19개 파라미터 + OFFSET/FETCH 2개)
     */
    private static final String SCHEDULE_DATA_SQL =
        "SELECT" +
        "    SI.SHPOKY, SI.SHPOIT, SI.SKUKEY, SI.DESC01," +
        "    TRIM(COALESCE(SI.SKUG05,''))  AS SKUG05," +
        "    CD.CDESC1                     AS SKUG05NM," +
        "    SI.UOMKEY," +
        "    CAST(COALESCE(SI.QTSHPO,0) AS NUMBER(18,4)) AS QTSHPO," +
        "    CAST(COALESCE(SI.QTSHPO - SI.QTALOC,0) AS NUMBER(18,4)) AS QTUALO," +
        "    CAST(COALESCE(SI.QTALOC,0) AS NUMBER(18,4)) AS QTALOC," +
        "    CAST(COALESCE(SI.QTJCMP,0) AS NUMBER(18,4)) AS QTJCMP," +
        "    CAST(COALESCE(SI.QTSHPD,0) AS NUMBER(18,4)) AS QTSHPD," +
        "    TRIM(COALESCE(SI.STATIT,''))  AS STATIT," +
        "    TRIM(COALESCE(SI.STDLNR,''))  AS STDLNR," +
        "    TRIM(COALESCE(SI.SVBELN,''))  AS SVBELN," +
        "    TRIM(COALESCE(SI.LOTA01,''))  AS LOTA01," +
        "    TRIM(COALESCE(SI.LOTA02,''))  AS LOTA02," +
        "    TRIM(COALESCE(SI.LOTA03,''))  AS LOTA03," +
        "    TRIM(COALESCE(SI.TLOTA01,'')) AS TLOTA01," +
        "    TRIM(COALESCE(SI.TLOTA02,'')) AS TLOTA02," +
        "    COALESCE((SELECT CDESC1 FROM KNRAWMS.CMCDV WHERE CMCDKY='LOTA02' AND CMCDVL=SI.LOTA02  AND ROWNUM=1),'')  AS LOTA02NM," +
        "    COALESCE((SELECT CDESC1 FROM KNRAWMS.CMCDV WHERE CMCDKY='LOTA02' AND CMCDVL=SI.TLOTA02 AND ROWNUM=1),'') AS TLOTA02NM," +
        "    SI.CREDAT, SI.CRETIM, SI.CREUSR," +
        "    SI.LMODAT, SI.LMOTIM, SI.LMOUSR," +
        "    SI.ALSTKY," +
        "    SH.PRTCHK," +
        "    SH.WAREKY," +
        "    SH.OWNRKY," +
        "    SH.DPTNKY," +
        "    COALESCE((SELECT NAME01 FROM KNRAWMS.BZPTN WHERE OWNRKY=SH.OWNRKY AND PTNRTY='CT' AND PTNRKY=SH.DPTNKY AND ROWNUM=1),'') AS DPTNM," +
        "    COALESCE((SELECT NAME01 FROM KNRAWMS.BZPTN WHERE OWNRKY=SH.OWNRKY AND PTNRTY='VD' AND PTNRKY=SH.PTRCVR AND ROWNUM=1),'') AS PTRCVRNM," +
        "    SH.RQSHPD, SH.DOCDAT," +
        "    SH.STATDO," +
        "    COALESCE(ST.CDESC1,'')        AS STATDONM," +
        "    SH.SHPMTY," +
        "    COALESCE((SELECT CDESC1 FROM KNRAWMS.CMCDV WHERE CMCDKY='TASOTY' AND CMCDVL=SH.SHPMTY AND ROWNUM=1),'') AS SHPMTYNM," +
        "    SH.DOCUTY                     AS DOCUTYNM," +
        "    SH.VEHINO," +
        "    SI.MEASKY," +
        "    CASE WHEN SI.SKUG05='10' AND M.GRSWGT IS NOT NULL AND M.GRSWGT > 0" +
        "         THEN M.GRSWGT" +
        "         ELSE NULL" +
        "    END                           AS PLTKG," +
        "    COALESCE((SELECT ME.QTAUOM  FROM KNRAWMS.MEASI ME  WHERE ME.WAREKY=SH.WAREKY AND ME.MEASKY=SI.MEASKY AND ME.UOMKEY='SOK' AND ROWNUM=1), NULL) AS SOK_PER_R," +
        "    COALESCE((SELECT ME2.QTAUOM FROM KNRAWMS.MEASI ME2 WHERE ME2.WAREKY=SH.WAREKY AND ME2.MEASKY=SI.MEASKY AND ME2.UOMKEY='KG'  AND ROWNUM=1), 0)    AS KG_PER_UNIT," +
        "    COALESCE((SELECT ME3.QTAUOM FROM KNRAWMS.MEASI ME3 WHERE ME3.WAREKY=SH.WAREKY AND ME3.MEASKY=SI.MEASKY AND ME3.UOMKEY='BAG' AND ROWNUM=1), 0)    AS BAG_PER_UNIT," +
        "    COALESCE((SELECT ME4.QTAUOM FROM KNRAWMS.MEASI ME4 WHERE ME4.WAREKY=SH.WAREKY AND ME4.MEASKY=SI.MEASKY AND ME4.UOMKEY='BOX' AND ROWNUM=1), 0)    AS BOX_PER_UNIT," +
        "    COALESCE((SELECT ME5.QTAUOM FROM KNRAWMS.MEASI ME5 WHERE ME5.WAREKY=SH.WAREKY AND ME5.MEASKY=SI.MEASKY AND ME5.UOMKEY='PAL' AND ROWNUM=1), 0)    AS PAL_PER_UNIT," +
        "    COALESCE((SELECT ME6.QTAUOM FROM KNRAWMS.MEASI ME6 WHERE ME6.WAREKY=SH.WAREKY AND ME6.MEASKY=SI.MEASKY AND ME6.UOMKEY='EA'  AND ROWNUM=1), 0)    AS EA_PER_UNIT" +
        " FROM KNRAWMS.SHPDI SI" +
        " INNER JOIN KNRAWMS.SHPDH SH ON SH.SHPOKY = SI.SHPOKY" +
        " LEFT  JOIN KNRAWMS.BZPTN CT ON CT.OWNRKY=SH.OWNRKY AND CT.PTNRTY='CT' AND CT.PTNRKY=SH.DPTNKY" +
        " LEFT  JOIN KNRAWMS.BZPTN VD ON VD.OWNRKY=SH.OWNRKY AND VD.PTNRTY='VD' AND VD.PTNRKY=SH.PTRCVR" +
        " LEFT  JOIN KNRAWMS.CMCDV ST ON ST.CMCDKY='STATDO' AND ST.CMCDVL=SH.STATDO" +
        " LEFT  JOIN KNRAWMS.CMCDV CD ON CD.CMCDKY='SKUG05' AND CD.CMCDVL=SI.SKUG05" +
        " LEFT  JOIN KNRAWMS.SKUMA M  ON SI.SKUKEY=M.SKUKEY AND SH.OWNRKY=M.OWNRKY" +
        " WHERE (? IS NULL OR SH.WAREKY = ?)" +
        "   AND (? IS NULL OR SH.RQSHPD >= ?)" +
        "   AND (? IS NULL OR SH.RQSHPD <= ?)" +
        "   AND (? IS NULL OR SH.STATDO = ?)" +
        "   AND (? IS NULL OR SI.SKUG05 = ?)" +
        "   AND (? IS NULL OR INSTR(',' || ? || ',', ',' || SI.LOTA02 || ',') > 0)" +
        "   AND (? IS NULL OR SI.SKUKEY LIKE ? OR SI.DESC01 LIKE ?)" +
        "   AND (? IS NULL OR SH.DPTNKY = ?)" +
        "   AND (? IS NULL OR INSTR(',' || ? || ',', ',' || SI.SVBELN || ',') > 0)" +
        " ORDER BY SI.SVBELN, SI.SHPOKY, SI.SHPOIT" +
        " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";    // p20,p21: offset, size

    public Map<String, Object> getSchedule(ShipmentSearchRequest req) {

        // ── 바인드 파라미터 결정 (null = 조건 스킵) ──────────────────────────
        String df = req.normalizedDateFrom();
        String dt = req.normalizedDateTo();

        String p1Wareky  = hasText(req.getWareky())   ? req.getWareky().strip()         : null;
        String p3DateFrom = hasText(df)               ? df                               : null;
        String p5DateTo   = hasText(dt)               ? dt                               : null;
        String p7Statdo   = hasText(req.getStatdo())  ? req.getStatdo().strip()          : null;
        String p9Skug05   = hasText(req.getSkug05())  ? req.getSkug05().strip()          : null;
        // lota02 IN → 쉼표 구분 문자열 (INSTR 패턴)
        String p11Lota02  = (req.getLota02List() != null && !req.getLota02List().isEmpty())
                            ? "," + String.join(",", req.getLota02List()) + "," : null;
        // keyword LIKE
        String p13Keyword = hasText(req.getKeyword()) ? "%" + req.getKeyword().trim() + "%" : null;
        // ptnrky: 다중선택(ptnrkyList) 우선, 없으면 단건 ptnrky 사용
        // 다중일 때는 INSTR 쉼표 패턴으로 변환 → 기존 (? IS NULL OR SH.DPTNKY = ?) 조건에 단건만 들어가므로
        // 다중의 경우 쉼표 구분 문자열 INSTR 방식 적용을 위해 getScheduleMulti 분기 처리
        List<String> ptnrkyListReq = req.getPtnrkyList();
        boolean ptnrkyMulti = ptnrkyListReq != null && ptnrkyListReq.size() > 1;
        String p16Ptnrky;
        if (ptnrkyMulti) {
            // 다중 → INSTR 패턴용 쉼표 구분 문자열 (단건 조건 대신 인라인 뷰로 처리)
            p16Ptnrky = null; // 아래 별도 처리
        } else if (ptnrkyListReq != null && ptnrkyListReq.size() == 1) {
            p16Ptnrky = ptnrkyListReq.get(0).strip();
        } else {
            p16Ptnrky = hasText(req.getPtnrky()) ? req.getPtnrky().strip() : null;
        }
        // svbeln 다중값 → INSTR 패턴용 쉼표 구분 문자열
        String p18Svbeln  = (req.getSvbeln() != null && !req.getSvbeln().isEmpty())
                            ? "," + req.getSvbeln().stream()
                                .map(String::strip)
                                .filter(s -> !s.isEmpty())
                                .collect(java.util.stream.Collectors.joining(",")) + ","
                            : null;
        // 실제로 유효한 값이 없으면 null로 처리
        if (p18Svbeln != null && p18Svbeln.equals(",,")) p18Svbeln = null;

        // ── 납품처 다중선택 IN절 동적 생성 ───────────────────────────────────
        // 단건(p16Ptnrky != null)이면 기존 고정 SQL 사용, 다중이면 IN절 추가
        List<String> ptnrkyInList = null;
        if (ptnrkyMulti) {
            ptnrkyInList = ptnrkyListReq.stream()
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        }
        final String ptnrkyInClause = (ptnrkyInList != null && !ptnrkyInList.isEmpty())
            ? " AND SH.DPTNKY IN (" + ptnrkyInList.stream().map(s -> "'" + s.replace("'","''") + "'").collect(Collectors.joining(",")) + ")"
            : "";

        // ── 전체 건수 ─────────────────────────────────────────────────────────
        String countSql = SCHEDULE_COUNT_SQL + ptnrkyInClause;
        var countQuery = em.createNativeQuery(countSql);
        setScheduleParams(countQuery, p1Wareky, p3DateFrom, p5DateTo,
                          p7Statdo, p9Skug05, p11Lota02, p13Keyword, p16Ptnrky, p18Svbeln);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        // ── 본문 쿼리 ─────────────────────────────────────────────────────────
        int size   = Math.max(1, Math.min(req.getSize(), 99999));
        int offset = (req.getPage() - 1) * size;   // 1-based page → 0-based offset

        // 다중 납품처 시 고정 SQL에 IN절 삽입 (ORDER BY 앞에 추가)
        String dataSql = ptnrkyInClause.isEmpty() ? SCHEDULE_DATA_SQL
            : SCHEDULE_DATA_SQL.replace(
                " ORDER BY SI.SVBELN",
                ptnrkyInClause + " ORDER BY SI.SVBELN");
        var dataQuery = em.createNativeQuery(dataSql);
        setScheduleParams(dataQuery, p1Wareky, p3DateFrom, p5DateTo,
                          p7Statdo, p9Skug05, p11Lota02, p13Keyword, p16Ptnrky, p18Svbeln);
        dataQuery.setParameter(20, offset);  // OFFSET ? ROWS
        dataQuery.setParameter(21, size);    // FETCH NEXT ? ROWS ONLY

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
        // 창고 목록 — 운영 창고 고정 목록 (9개)
        List<ShipmentFilterOptsResponse.CodeLabel> warekyList = Arrays.asList(
            ShipmentFilterOptsResponse.CodeLabel.builder().value("1100").label("청주공장창고").build(),
            ShipmentFilterOptsResponse.CodeLabel.builder().value("1500").label("음성공장창고").build(),
            ShipmentFilterOptsResponse.CodeLabel.builder().value("4100").label("용인물류센터").build(),
            ShipmentFilterOptsResponse.CodeLabel.builder().value("4400").label("경북물류센터").build(),
            ShipmentFilterOptsResponse.CodeLabel.builder().value("4200").label("경남물류센터").build(),
            ShipmentFilterOptsResponse.CodeLabel.builder().value("4300").label("전라물류센터").build(),
            ShipmentFilterOptsResponse.CodeLabel.builder().value("4800").label("제주물류센터").build(),
            ShipmentFilterOptsResponse.CodeLabel.builder().value("4000").label("파주물류센터").build(),
            ShipmentFilterOptsResponse.CodeLabel.builder().value("6000").label("임가공 거점").build()
        );

        // 출고상태 목록
        @SuppressWarnings("unchecked")
        List<Object[]> statitRows = em.createNativeQuery(
            "SELECT CMCDVL AS value, CDESC1 AS label FROM KNRAWMS.CMCDV WHERE CMCDKY='STATIT' ORDER BY CMCDVL"
        ).getResultList();
        List<ShipmentFilterOptsResponse.CodeLabel> statitList = statitRows.stream()
            .map(row -> ShipmentFilterOptsResponse.CodeLabel.builder()
                .value(str(row[0])).label(str(row[1])).build())
            .collect(Collectors.toList());

        // SKUG05 목록 — CMCDV WHERE CMCDKY='SKUG05' (CMCDVL→value, CDESC1→label)
        @SuppressWarnings("unchecked")
        List<Object[]> skug05Rows = em.createNativeQuery(
            "SELECT CMCDVL AS value, CDESC1 AS label FROM KNRAWMS.CMCDV WHERE CMCDKY='SKUG05' ORDER BY CMCDVL"
        ).getResultList();
        List<ShipmentFilterOptsResponse.CodeLabel> skug05List = skug05Rows.stream()
            .map(row -> ShipmentFilterOptsResponse.CodeLabel.builder()
                .value(str(row[0])).label(str(row[1])).build())
            .collect(Collectors.toList());

        // LOTA02(플랜트) 목록 — CMCDV WHERE CMCDKY='LOTA02' (CMCDVL→value, CDESC1→label)
        @SuppressWarnings("unchecked")
        List<Object[]> lota02Rows = em.createNativeQuery(
            "SELECT CMCDVL AS value, CDESC1 AS label FROM KNRAWMS.CMCDV WHERE CMCDKY='LOTA02' ORDER BY CMCDVL"
        ).getResultList();
        List<ShipmentFilterOptsResponse.CodeLabel> lota02List = lota02Rows.stream()
            .map(row -> ShipmentFilterOptsResponse.CodeLabel.builder()
                .value(str(row[0])).label(str(row[1])).build())
            .collect(Collectors.toList());

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

    /**
     * 출고현황 고정 SQL 공통 파라미터 바인딩 헬퍼
     * COUNT SQL / DATA SQL 동일한 1~19번 파라미터 구조 공유
     * DATA SQL은 추가로 p20=offset, p21=size 필요
     *
     * p1,p2:     wareky  — (? IS NULL OR SH.WAREKY = ?)
     * p3,p4:     dateFrom— (? IS NULL OR SH.RQSHPD >= ?)
     * p5,p6:     dateTo  — (? IS NULL OR SH.RQSHPD <= ?)
     * p7,p8:     statdo  — (? IS NULL OR SH.STATDO = ?)
     * p9,p10:    skug05  — (? IS NULL OR SI.SKUG05 = ?)
     * p11,p12:   lota02  — (? IS NULL OR INSTR(','||?||',', ','||SI.LOTA02||',') > 0)
     * p13,p14,p15: keyword — (? IS NULL OR SI.SKUKEY LIKE ? OR SI.DESC01 LIKE ?)
     * p16,p17:   ptnrky  — (? IS NULL OR SH.DPTNKY = ?)
     * p18,p19:   svbeln  — (? IS NULL OR INSTR(','||?||',', ','||SI.SVBELN||',') > 0)
     */
    private void setScheduleParams(Query q,
                                   String wareky, String dateFrom, String dateTo,
                                   String statdo, String skug05, String lota02,
                                   String keyword, String ptnrky, String svbeln) {
        q.setParameter(1,  wareky);   q.setParameter(2,  wareky);
        q.setParameter(3,  dateFrom); q.setParameter(4,  dateFrom);
        q.setParameter(5,  dateTo);   q.setParameter(6,  dateTo);
        q.setParameter(7,  statdo);   q.setParameter(8,  statdo);
        q.setParameter(9,  skug05);   q.setParameter(10, skug05);
        q.setParameter(11, lota02);   q.setParameter(12, lota02);
        q.setParameter(13, keyword);  q.setParameter(14, keyword);  q.setParameter(15, keyword);
        q.setParameter(16, ptnrky);   q.setParameter(17, ptnrky);
        q.setParameter(18, svbeln);   q.setParameter(19, svbeln);
    }
}
