package com.company.module.shipment.service;

import com.company.module.shipment.dto.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    /** 완성쿼리 로거 — stdout.log 에 [BOUND-SQL] 태그로 기록 */
    private static final Logger qlog = LoggerFactory.getLogger("TMS_QUERY_LOG");

    private static final double PLT_CAP_KG = 1200.0;

    @PersistenceContext(unitName = "wmsPU")
    private EntityManager em;

    // ──────────────────────────────────────────────────────────────────────────
    // 출고진행현황 조회
    //
    // ■ 동적 WHERE 설계 (소프트파싱용 '? IS NULL OR ...' 고정조건 제거)
    //   기존에는 모든 조건을 (? IS NULL OR col ...) 형태로 항상 SQL 에 포함했으나,
    //   값이 존재하는 조건만 WHERE 절 + 바인드 파라미터로 추가하도록 전환.
    // ■ shpmty / ptnrky 다중선택은 동적 IN 절로 SQL 문자열에 직접 삽입
    // ──────────────────────────────────────────────────────────────────────────

    /** 출고현황 COUNT SQL BASE (WHERE 는 동적 구성) */
    private static final String SCHEDULE_COUNT_BASE_SQL =
        "SELECT COUNT(*)" +
        " FROM KNRAWMS.SHPDI SI" +
        " INNER JOIN KNRAWMS.SHPDH SH ON SH.SHPOKY = SI.SHPOKY" +
        " LEFT  JOIN KNRAWMS.SKUMA M  ON SI.SKUKEY = M.SKUKEY AND SH.OWNRKY = M.OWNRKY";

    /** 출고현황 본문 SQL ORDER BY */
    private static final String SCHEDULE_DATA_ORDER_BY =
        " ORDER BY SI.SVBELN, SI.SHPOKY, SI.SHPOIT";

    /**
     * 출고현황 본문 SQL BASE (SELECT + JOIN, WHERE 는 동적 구성)
     */
    private static final String SCHEDULE_DATA_BASE_SQL =
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
        " LEFT  JOIN KNRAWMS.SKUMA M  ON SI.SKUKEY=M.SKUKEY AND SH.OWNRKY=M.OWNRKY";

    // ── shpmty IN절 동적 생성 헬퍼 (리터럴 삽입 — 가변 목록) ────────────────
    private String buildShpmtyClause(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        String inVals = list.stream()
            .map(String::strip)
            .filter(s -> !s.isEmpty())
            .distinct()
            .map(s -> "'" + s.replace("'", "''") + "'")
            .collect(Collectors.joining(","));
        return inVals.isEmpty() ? "" : " AND SH.SHPMTY IN (" + inVals + ")";
    }

    /**
     * 동적 WHERE 구성 — 값이 존재하는 조건만 WHERE 절 + 바인드 파라미터(params)로 추가한다.
     * IN 절(shpmty / ptnrky 다중선택)은 가변 목록이므로 리터럴로 직접 삽입한다.
     */
    private String buildScheduleWhere(ShipmentSearchRequest req, List<Object> params) {
        String df = req.normalizedDateFrom();
        String dt = req.normalizedDateTo();

        String wareky   = hasText(req.getWareky())  ? req.getWareky().strip()  : null;
        String dateFrom = hasText(df)               ? df                       : null;
        String dateTo   = hasText(dt)               ? dt                       : null;
        String statdo   = hasText(req.getStatdo())  ? req.getStatdo().strip()  : null;
        String skug05   = hasText(req.getSkug05())  ? req.getSkug05().strip()  : null;
        // lota02 IN → 쉼표 구분 문자열 (INSTR 패턴)
        String lota02   = (req.getLota02List() != null && !req.getLota02List().isEmpty())
                          ? "," + String.join(",", req.getLota02List()) + "," : null;
        // keyword LIKE
        String keyword  = hasText(req.getKeyword()) ? "%" + req.getKeyword().trim() + "%" : null;
        // ptnrky: 다중선택(ptnrkyList) 우선, 없으면 단건 ptnrky 사용
        List<String> ptnrkyListReq = req.getPtnrkyList();
        boolean ptnrkyMulti = ptnrkyListReq != null && ptnrkyListReq.size() > 1;
        String ptnrky;
        if (ptnrkyMulti) {
            ptnrky = null; // 아래 IN절 별도 처리
        } else if (ptnrkyListReq != null && ptnrkyListReq.size() == 1) {
            ptnrky = ptnrkyListReq.get(0).strip();
        } else {
            ptnrky = hasText(req.getPtnrky()) ? req.getPtnrky().strip() : null;
        }
        // svbeln 다중값 → INSTR 패턴용 쉼표 구분 문자열
        String svbeln  = (req.getSvbeln() != null && !req.getSvbeln().isEmpty())
                          ? "," + req.getSvbeln().stream()
                              .map(String::strip)
                              .filter(s -> !s.isEmpty())
                              .collect(Collectors.joining(",")) + ","
                          : null;
        if (svbeln != null && svbeln.equals(",,")) svbeln = null;

        StringBuilder where = new StringBuilder();
        if (wareky != null) {
            where.append(where.length() == 0 ? " WHERE" : " AND").append(" SH.WAREKY = ?");
            params.add(wareky);
        }
        if (dateFrom != null) {
            where.append(where.length() == 0 ? " WHERE" : " AND").append(" SH.RQSHPD >= ?");
            params.add(dateFrom);
        }
        if (dateTo != null) {
            where.append(where.length() == 0 ? " WHERE" : " AND").append(" SH.RQSHPD <= ?");
            params.add(dateTo);
        }
        if (statdo != null) {
            where.append(where.length() == 0 ? " WHERE" : " AND").append(" SH.STATDO = ?");
            params.add(statdo);
        }
        if (skug05 != null) {
            where.append(where.length() == 0 ? " WHERE" : " AND").append(" SI.SKUG05 = ?");
            params.add(skug05);
        }
        if (lota02 != null) {
            where.append(where.length() == 0 ? " WHERE" : " AND")
                 .append(" INSTR(',' || ? || ',', ',' || SI.LOTA02 || ',') > 0");
            params.add(lota02);
        }
        if (keyword != null) {
            where.append(where.length() == 0 ? " WHERE" : " AND")
                 .append(" (SI.SKUKEY LIKE ? OR SI.DESC01 LIKE ?)");
            params.add(keyword);
            params.add(keyword);
        }
        if (ptnrky != null) {
            where.append(where.length() == 0 ? " WHERE" : " AND").append(" SH.DPTNKY = ?");
            params.add(ptnrky);
        }
        if (svbeln != null) {
            where.append(where.length() == 0 ? " WHERE" : " AND")
                 .append(" INSTR(',' || ? || ',', ',' || SI.SVBELN || ',') > 0");
            params.add(svbeln);
        }

        // ── 납품처 다중선택 IN절 (리터럴 삽입) ───────────────────────────────
        if (ptnrkyMulti) {
            List<String> ptnrkyInList = ptnrkyListReq.stream()
                .map(String::strip).filter(s -> !s.isEmpty()).distinct()
                .collect(Collectors.toList());
            if (!ptnrkyInList.isEmpty()) {
                where.append(where.length() == 0 ? " WHERE" : " AND")
                     .append(" SH.DPTNKY IN (")
                     .append(ptnrkyInList.stream()
                        .map(s -> "'" + s.replace("'", "''") + "'")
                        .collect(Collectors.joining(",")))
                     .append(")");
            }
        }
        // ── 출하유형(shpmty) IN절 (리터럴 삽입) ──────────────────────────────
        String shpmtyClause = buildShpmtyClause(req.getShpmtyList());
        if (!shpmtyClause.isEmpty()) {
            // buildShpmtyClause 는 " AND ..." 로 시작 → WHERE 가 비어있으면 접두 보정
            where.append(where.length() == 0 ? shpmtyClause.replaceFirst(" AND", " WHERE") : shpmtyClause);
        }
        return where.toString();
    }

    public Map<String, Object> getSchedule(ShipmentSearchRequest req) {

        // ── 동적 WHERE + 바인드 파라미터 구성 ────────────────────────────────
        List<Object> params = new ArrayList<>();
        String whereSql = buildScheduleWhere(req, params);

        // ── 전체 건수 ─────────────────────────────────────────────────────────
        String countSql = SCHEDULE_COUNT_BASE_SQL + whereSql;
        var countQuery = em.createNativeQuery(countSql);
        for (int i = 0; i < params.size(); i++) countQuery.setParameter(i + 1, params.get(i));
        long total = ((Number) countQuery.getSingleResult()).longValue();

        // ── 본문 쿼리 ─────────────────────────────────────────────────────────
        int size   = Math.max(1, Math.min(req.getSize(), 99999));
        int offset = (req.getPage() - 1) * size;   // 1-based page → 0-based offset

        String dataSql = SCHEDULE_DATA_BASE_SQL + whereSql + SCHEDULE_DATA_ORDER_BY
            + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        var dataQuery = em.createNativeQuery(dataSql);
        int bi = 0;
        for (; bi < params.size(); bi++) dataQuery.setParameter(bi + 1, params.get(bi));
        dataQuery.setParameter(bi + 1, offset);  // OFFSET ? ROWS
        dataQuery.setParameter(bi + 2, size);    // FETCH NEXT ? ROWS ONLY

        // ── 완성쿼리 로그 출력 (stdout.log) ──────────────────────────────────
        logBoundQuery(dataSql, params, offset, size);

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
    // 완성쿼리 로그 — stdout.log 에 [BOUND-SQL] 태그로 기록
    // ──────────────────────────────────────────────────────────────────────────
    private void logBoundQuery(String sql, List<Object> params, int offset, int size) {
        try {
            // ? 를 순서대로 실제 값으로 치환하여 완성쿼리 생성
            //   동적 WHERE 파라미터(params) + OFFSET/SIZE 2개
            List<String> bindVals = new ArrayList<>();
            for (Object p : params) bindVals.add(q(p == null ? null : p.toString()));
            bindVals.add(String.valueOf(offset));
            bindVals.add(String.valueOf(size));

            StringBuilder bound = new StringBuilder();
            int bi = 0;
            for (char c : sql.toCharArray()) {
                if (c == '?' && bi < bindVals.size()) {
                    bound.append(bindVals.get(bi++));
                } else {
                    bound.append(c);
                }
            }

            qlog.info("[QUERY-PARAMS] bound-count={}, values={}", bindVals.size(), bindVals);
            qlog.info("[BOUND-SQL]\n{}", bound);
        } catch (Exception e) {
            qlog.warn("[BOUND-SQL] 로그 출력 실패: {}", e.getMessage());
        }
    }

    /** SQL 리터럴용 null-safe 인용 헬퍼 */
    private String q(String v) {
        return v == null ? "NULL" : "'" + v.replace("'", "''") + "'";
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

        // 최대 납품요청일(maxDate) 조회 로직 제거 — 기본 날짜 범위는 프론트에서 오늘 기준(-3, +1)으로 계산.
        //   (기존 findMaxRqshpd() 지연 쿼리 제거로 filter-opts 응답 지연 해소)

        return ShipmentFilterOptsResponse.builder()
            .warekyList(warekyList)
            .statitList(statitList)
            .skug05List(skug05List)
            .lota02List(lota02List)
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
        String  boxbag    = boxPer > 0 ? "BOX" : (bagPer > 0 ? "BAG" : "");
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
