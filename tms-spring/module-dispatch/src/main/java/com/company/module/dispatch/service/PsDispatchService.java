package com.company.module.dispatch.service;

import com.company.module.dispatch.dto.*;
import com.company.module.dispatch.entity.PsDispatchH;
import com.company.module.dispatch.entity.PsDispatchI;
import com.company.module.dispatch.repository.PsDispatchHRepository;
import com.company.module.dispatch.repository.PsDispatchIRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PS배차 서비스
 * Flask: api_ps_dispatch_search / api_ps_dispatch_save / api_ps_dispatch_list
 *        / api_ps_dispatch_confirm 대응
 *
 * ■ DataSource 라우팅
 *   - em     (wmsPU, Oracle KNRAWMS): SHPDI, SHPDH, BZPTN, CMCDV, SKUMA, RECDI
 *   - tmsEm  (tmsPU, Oracle KNRAWMS): PS_DISPATCH_H, PS_DISPATCH_D, DS_VEHICLE
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "tmsTransactionManager")
public class PsDispatchService {

    private final PsDispatchHRepository dispatchHRepo;
    private final PsDispatchIRepository dispatchIRepo;

    /** Oracle WMS — KNRAWMS.SHPDI / SHPDH / BZPTN / CMCDV / SKUMA / RECDI */
    @PersistenceContext(unitName = "wmsPU")
    private EntityManager em;

    /** Oracle KNRAWMS TMS — PS_DISPATCH_H / PS_DISPATCH_D / DS_VEHICLE */
    @PersistenceContext(unitName = "tmsPU")
    private EntityManager tmsEm;

    // ──────────────────────────────────────────────────────────────────────────
    // SKUKEY 파싱 헬퍼 (Flask _ps_* 함수 Java 포팅)
    // ──────────────────────────────────────────────────────────────────────────

    private static final Set<String> INCH12_CODES = Set.of("s12","s13","s14","p12","p13","p14","r12","r13");
    private static final Set<String> INCH3_CODES  = Set.of("s03","p03","r03","s3i","p3i");

    private String psGetInch(String skukey) {
        if (skukey == null || skukey.length() < 5) return "";
        String m = skukey.toLowerCase().substring(2, 5);
        if (INCH12_CODES.contains(m)) return "12인치";
        if (INCH3_CODES.contains(m))  return "3인치";
        return "";
    }

    private String psGetGrm(String skukey) {
        try {
            int g = Integer.parseInt(skukey.substring(5, 8));
            return g >= 300 ? "GE300" : "LT300";
        } catch (Exception e) {
            return "LT300";
        }
    }

    private boolean psIsRoll(String skukey) {
        if (skukey == null || skukey.length() < 17) return false;
        if (!"-".equals(skukey.substring(8, 9))) return false;
        return "0000".equals(skukey.substring(13, 17));
    }

    private boolean psIsBoard(String skukey) {
        if (skukey == null || skukey.length() < 17) return false;
        if (!"-".equals(skukey.substring(8, 9))) return false;
        String lp = skukey.substring(13, 17);
        return !"0000".equals(lp) && lp.chars().allMatch(Character::isDigit);
    }

    /** (gsm, width_mm) 파싱 – SKUKEY[5:8], SKUKEY[9:13] */
    private int[] psParseSkukeyDims(String skukey) {
        if (skukey == null || skukey.length() < 17) return null;
        try {
            int gsm = Integer.parseInt(skukey.substring(5, 8));
            int w   = Integer.parseInt(skukey.substring(9, 13));
            return new int[]{gsm, w};
        } catch (Exception e) { return null; }
    }

    /** (width_mm, length_mm) 파싱 – 판지 전용 */
    private int[] psParseboardDims(String skukey) {
        if (skukey == null || skukey.length() < 17) return null;
        if (!"-".equals(skukey.substring(8, 9))) return null;
        try {
            int w = Integer.parseInt(skukey.substring(9, 13));
            int l = Integer.parseInt(skukey.substring(13, 17));
            return new int[]{w, l};
        } catch (Exception e) { return null; }
    }

    private double psCalcRollCbmPerRoll(int gsm, int widthMm, double rollSingleKg) {
        // 직경 계산: D = sqrt(4 * 총면적 / (π × 지폭)) 단위 mm
        // 총면적(m²) = rollSingleKg / (gsm/1e6)
        double areaMm2 = (rollSingleKg / (gsm / 1_000_000.0));
        double dMm = Math.sqrt(4.0 * areaMm2 / (Math.PI * widthMm));
        double rM  = (dMm / 2.0) / 1000.0;
        double wM  = widthMm / 1000.0;
        return Math.round(Math.PI * rM * rM * wM * 10000.0) / 10000.0;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 배차번호 채번 (Flask _ps_next_dispatch_no)
    // MariaDB PS_DISPATCH_H 에서 채번 → tmsTransactionManager
    // ──────────────────────────────────────────────────────────────────────────
    @Transactional(transactionManager = "tmsTransactionManager")
    public String nextDispatchNo(String yyyymmdd) {
        String dt = yyyymmdd == null ? "" : yyyymmdd.replace("-", "");
        String yymmdd = dt.length() == 8 ? dt.substring(2) : (dt.length() >= 6 ? dt.substring(dt.length() - 6) : dt);
        String prefix = yymmdd;
        Optional<String> maxOpt = dispatchHRepo.findMaxDispatchNoByPrefix(prefix);
        int seq = 1;
        if (maxOpt.isPresent() && maxOpt.get() != null && maxOpt.get().length() == 10) {
            try { seq = Integer.parseInt(maxOpt.get().substring(6, 9)) + 1; }
            catch (Exception ignored) {}
        }
        return String.format("%s%03dT", yymmdd, seq);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 납품문서 검색 (Flask api_ps_dispatch_search)
    // Oracle WMS: SHPDI, SHPDH, BZPTN, CMCDV, SKUMA, RECDI → em(wmsPU)
    // ──────────────────────────────────────────────────────────────────────────
    public List<PsDispatchDocResponse> searchDocs(PsDispatchSearchRequest req) {
        String dateFrom  = req.normalizedDateFrom();
        String dateTo    = req.normalizedDateTo();
        String dptnky    = req.getDptnky();
        String shpoky    = req.getShpoky();
        List<String> shpmtyList = req.getShpmty();
        String dispStat  = req.getStatus() == null ? "all" : req.getStatus();

        // 배차완료 키 목록 (Oracle KNRAWMS.SHPDI → em)
        @SuppressWarnings("unchecked")
        List<String> dispatchedKeys = em.createNativeQuery("""
            SELECT CONCAT(SHPOKY,'|',SHPOIT)
            FROM KNRAWMS.SHPDI
            WHERE STATIT='NEW' AND TRIM(STDLNR) != ''
            """).getResultList();
        Set<String> dispatchedSet = new HashSet<>(dispatchedKeys);

        // 동적 WHERE 구성 (Oracle KNRAWMS 테이블 → em)
        StringBuilder sb = new StringBuilder("""
            SELECT i.SHPOKY, i.SHPOIT, i.SKUKEY, i.DESC01,
                   TRIM(COALESCE(i.SVBELN,'')) AS SVBELN,
                   i.UOMKEY, CAST(i.QTSHPO AS DECIMAL(18,4)) AS QTSHPO,
                   TRIM(COALESCE(i.SKUG05,'')) AS SKUG05,
                   h.DPTNKY,
                   TRIM(COALESCE(b.NAME01,'')) AS DPTNM,
                   h.DOCDAT, h.RQSHPD, h.SHPMTY,
                   TRIM(COALESCE(c.CDESC1,'')) AS SHPMTY_NM,
                   COALESCE(m.GRSWGT, 0) AS GRSWGT,
                   TRIM(COALESCE(i.LOTA03,'')) AS LOTA03,
                   COALESCE(rd.QTYRCV, 0) AS UNIT_WEIGHT
            FROM KNRAWMS.SHPDI i
            JOIN KNRAWMS.SHPDH h ON i.SHPOKY = h.SHPOKY
            LEFT JOIN KNRAWMS.BZPTN b ON b.PTNRKY = h.DPTNKY AND b.PTNRTY = 'CT'
            LEFT JOIN KNRAWMS.CMCDV c ON c.CMCDKY = 'TASOTY' AND c.CMCDVL = h.SHPMTY
            LEFT JOIN KNRAWMS.SKUMA m ON m.SKUKEY = i.SKUKEY
            LEFT JOIN KNRAWMS.RECDI rd ON rd.SKUKEY = i.SKUKEY
            WHERE TRIM(i.SKUG05) = '10' AND h.WAREKY = '1100'
            """);

        List<Object> params = new ArrayList<>();
        if (dateFrom != null && !dateFrom.isEmpty()) {
            sb.append(" AND h.RQSHPD >= ?"); params.add(dateFrom);
        }
        if (dateTo != null && !dateTo.isEmpty()) {
            sb.append(" AND h.RQSHPD <= ?"); params.add(dateTo);
        }
        if (dptnky != null && !dptnky.isEmpty()) {
            sb.append(" AND (h.DPTNKY LIKE ? OR TRIM(COALESCE(b.NAME01,'')) LIKE ?)");
            params.add("%" + dptnky + "%");
            params.add("%" + dptnky + "%");
        }
        if (shpoky != null && !shpoky.isEmpty()) {
            sb.append(" AND (i.SHPOKY LIKE ? OR i.SVBELN LIKE ?)");
            params.add("%" + shpoky + "%");
            params.add("%" + shpoky + "%");
        }
        if (shpmtyList != null && !shpmtyList.isEmpty()) {
            String ph = shpmtyList.stream().map(x -> "?").collect(Collectors.joining(","));
            sb.append(" AND h.SHPMTY IN (").append(ph).append(")");
            params.addAll(shpmtyList);
        }
        sb.append(" ORDER BY h.RQSHPD, h.DPTNKY, i.SHPOKY, i.SHPOIT");

        // Oracle em으로 실행
        var query = em.createNativeQuery(sb.toString());
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<PsDispatchDocResponse> result = new ArrayList<>();

        for (Object[] r : rows) {
            String sk      = str(r[2]);
            String svbeln  = str(r[4]);
            String uomkey  = str(r[5]).strip();
            double qtshpo  = toDouble(r[6]);
            double grswgt  = toDouble(r[14]);
            double unitW   = toDouble(r[16]);

            String key    = str(r[0]) + "|" + str(r[1]);
            boolean isDisp = dispatchedSet.contains(key);
            if ("dispatched".equals(dispStat) && !isDisp) continue;
            if ("undispatched".equals(dispStat) && isDisp) continue;

            String skuType = psIsRoll(sk) ? "roll" : psIsBoard(sk) ? "board" : "other";

            // 판지 GRSWGT 역산 (SKUMA 미등록 시)
            if (grswgt <= 0 && "R".equals(uomkey) && psIsBoard(sk)) {
                int[] dims   = psParseSkukeyDims(sk);
                int[] bDims  = psParseboardDims(sk);
                if (dims != null && bDims != null) {
                    grswgt = Math.round(500.0 * (dims[0] / 1_000_000.0) * bDims[0] * bDims[1] / 1000.0 * 100.0) / 100.0;
                }
            }

            double kgWeight;
            if ("R".equals(uomkey) && grswgt > 0) kgWeight = Math.round(qtshpo * grswgt * 100.0) / 100.0;
            else kgWeight = qtshpo;

            double rollSingleKg = unitW > 0 ? unitW : 600.0;
            int rollCount = 0;
            if ("roll".equals(skuType)) {
                rollCount = "R".equals(uomkey) ? (int) qtshpo
                        : (kgWeight > 0 ? (int) Math.ceil(kgWeight / rollSingleKg) : 0);
            }

            double rollCbm = 0.0;
            if ("roll".equals(skuType)) {
                int[] dims = psParseSkukeyDims(sk);
                if (dims != null) {
                    rollCbm = psCalcRollCbmPerRoll(dims[0], dims[1], rollSingleKg) * rollCount;
                }
            }

            double boardCbm = 0.0;
            if ("board".equals(skuType)) {
                int[] bDims = psParseboardDims(sk);
                int[] dims  = psParseSkukeyDims(sk);
                if (bDims != null && dims != null && dims[0] > 0 && grswgt > 0) {
                    double areaMm2  = (double) bDims[0] * bDims[1];
                    double tSheetM  = (dims[0] / 1000.0) / 1200.0;
                    double grswgtG  = grswgt * 1000.0;
                    double gsmPerMm2 = dims[0] / 1_000_000.0;
                    double sheets   = gsmPerMm2 * areaMm2 > 0 ? grswgtG / (gsmPerMm2 * areaMm2) : 0;
                    double hPerBm   = sheets * tSheetM;
                    double areaM2   = (bDims[0] / 1000.0) * (bDims[1] / 1000.0);
                    double cbmPerB  = areaM2 * hPerBm;
                    double bundles  = "R".equals(uomkey) ? qtshpo : (grswgt > 0 ? kgWeight / grswgt : 0);
                    boardCbm = Math.round(cbmPerB * bundles * 10000.0) / 10000.0;
                }
            }

            result.add(PsDispatchDocResponse.builder()
                .shpoky(str(r[0]))
                .shpoit(str(r[1]))
                .skukey(sk)
                .desc01(str(r[3]))
                .svbeln(svbeln)
                .uomkey(uomkey)
                .qtshpo(qtshpo)
                .grswgt(grswgt)
                .kgWeight(kgWeight)
                .rollCbm(rollCbm)
                .boardCbm(boardCbm)
                .unitWeight(unitW)
                .rollCount(rollCount)
                .skug05(str(r[7]))
                .skuType(skuType)
                .dptnky(str(r[8]))
                .dptnm(str(r[9]))
                .docdat(str(r[10]))
                .rqshpd(str(r[11]))
                .shpmty(str(r[12]))
                .shpmtyNm(str(r[13]))
                .inch("board".equals(skuType) ? "" : psGetInch(sk))
                .grmCond(psGetGrm(sk))
                .dispatched(isDisp)
                .lota03(str(r[15]))
                .build());
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 배차 저장 (Flask api_ps_dispatch_save)
    // PS_DISPATCH_H/D → MariaDB tmsEm
    // SHPDI.STDLNR, SHPDH.VEHINO 업데이트 → Oracle em
    // ──────────────────────────────────────────────────────────────────────────
    @Transactional(transactionManager = "tmsTransactionManager")
    public List<String> saveDispatch(PsDispatchSaveRequest req) {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
        List<String> saved = new ArrayList<>();

        for (PsDispatchSaveRequest.VehicleBlock veh : req.getVehicles()) {
            String dt          = veh.getRqshpd() == null ? today : veh.getRqshpd().replace("-", "");
            String dispatchNo  = nextDispatchNo(dt);
            String carclassCd  = veh.getCarclassCd() == null ? "" : veh.getCarclassCd().strip();
            double totalKg     = veh.getTotalKg() == null ? 0.0 : veh.getTotalKg();
            int    totalCnt    = veh.getItems() == null ? 0 : veh.getItems().size();

            // PS_DISPATCH_H INSERT → MariaDB tmsEm
            tmsEm.createNativeQuery("""
                INSERT INTO KNRAWMS.PS_DISPATCH_H
                  (DISPATCH_NO, DISPATCH_DT, RQSHPD, DPTNKY, DPTNM,
                   CARTYPE, STATUS, TOTAL_KG, TOTAL_CNT, CREDAT, CREUSR)
                VALUES (?,?,?,?,?,?,'DRAFT',?,?,?,?)
                """)
              .setParameter(1,  dispatchNo)
              .setParameter(2,  today)
              .setParameter(3,  dt)
              .setParameter(4,  veh.getDptnky())
              .setParameter(5,  veh.getDptnm())
              .setParameter(6,  veh.getCartype())
              .setParameter(7,  totalKg)
              .setParameter(8,  totalCnt)
              .setParameter(9,  today)
              .setParameter(10, "SYSTEM")
              .executeUpdate();

            List<PsDispatchSaveRequest.ItemBlock> items = veh.getItems();
            Set<String> shpokySet   = new LinkedHashSet<>();
            List<String[]> shpdiKeys = new ArrayList<>();

            if (items != null) {
                int seq = 1;
                for (PsDispatchSaveRequest.ItemBlock it : items) {
                    // PS_DISPATCH_D INSERT → MariaDB tmsEm
                    tmsEm.createNativeQuery("""
                        INSERT INTO KNRAWMS.PS_DISPATCH_D
                          (DISPATCH_NO,SEQ,SHPOKY,SHPOIT,SKUKEY,DESC01,
                           QTSHPO,UOMKEY,DPTNKY,DPTNM,IS_SPLIT,ORG_SHPOKY,ORG_SHPOIT,
                           GRSWGT,KG_WEIGHT)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """)
                      .setParameter(1,  dispatchNo)
                      .setParameter(2,  seq++)
                      .setParameter(3,  it.getShpoky())
                      .setParameter(4,  it.getShpoit())
                      .setParameter(5,  it.getSkukey())
                      .setParameter(6,  it.getDesc01())
                      .setParameter(7,  it.getQtshpo() == null ? 0.0 : it.getQtshpo())
                      .setParameter(8,  it.getUomkey() == null ? "KG" : it.getUomkey())
                      .setParameter(9,  it.getDptnky())
                      .setParameter(10, it.getDptnm())
                      .setParameter(11, it.getIsSplit() == null ? 0 : it.getIsSplit())
                      .setParameter(12, it.getOrgShpoky())
                      .setParameter(13, it.getOrgShpoit())
                      .setParameter(14, it.getGrswgt() == null ? 0.0 : it.getGrswgt())
                      .setParameter(15, it.getKgWeight() == null ? 0.0 : it.getKgWeight())
                      .executeUpdate();

                    if (it.getShpoky() != null && it.getShpoit() != null) {
                        shpdiKeys.add(new String[]{it.getShpoky(), it.getShpoit()});
                    }
                    if (it.getShpoky() != null) shpokySet.add(it.getShpoky());
                }
            }

            // SHPDI.STDLNR = DISPATCH_NO (Oracle KNRAWMS.SHPDI 업데이트 → em)
            for (String[] key : shpdiKeys) {
                em.createNativeQuery("""
                    UPDATE KNRAWMS.SHPDI
                    SET STDLNR  = ?,
                        LMODAT  = TO_CHAR(SYSDATE, 'YYYYMMDD'),
                        LMOUSR  = 'WEB'
                    WHERE SHPOKY = ? AND SHPOIT = ? AND STATIT = 'NEW'
                    """)
                  .setParameter(1, dispatchNo)
                  .setParameter(2, key[0])
                  .setParameter(3, key[1])
                  .executeUpdate();
            }

            // SHPDH.VEHINO = carclass_cd (Oracle KNRAWMS.SHPDH 업데이트 → em)
            if (!shpokySet.isEmpty()) {
                String ph = shpokySet.stream().map(x -> "?").collect(Collectors.joining(","));
                var q = em.createNativeQuery(
                    "UPDATE KNRAWMS.SHPDH SET VEHINO=?, CARTON=?, CARNO=NULL, DRIVER=NULL, DRIVERCEL=NULL," +
                    " LMODAT=TO_CHAR(SYSDATE,'YYYYMMDD'), LMOUSR='WEB' WHERE SHPOKY IN (" + ph + ")"
                );
                q.setParameter(1, carclassCd.isEmpty() ? null : carclassCd);
                q.setParameter(2, carclassCd.isEmpty() ? null : carclassCd);
                int idx = 3;
                for (String sk : shpokySet) { q.setParameter(idx++, sk); }
                q.executeUpdate();
            }

            saved.add(dispatchNo);
        }
        return saved;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 배차 목록 조회 (Flask api_ps_dispatch_list)
    // PS_DISPATCH_H + ds_vehicle → MariaDB tmsEm
    // PS_DISPATCH_D 조회 후 RECDI(Oracle) 별도 조회 → Cross-DB join 분리
    // ──────────────────────────────────────────────────────────────────────────
    public List<PsDispatchListResponse> getList(PsDispatchListRequest req) {
        String dateFrom   = req.normalizedDateFrom();
        String dateTo     = req.normalizedDateTo();
        String dptnky     = req.getDptnky();
        String status     = req.getStatus();
        String dispatchNo = req.getDispatchNo();

        // PS_DISPATCH_H + ds_vehicle → MariaDB tmsEm
        StringBuilder sb = new StringBuilder("""
            SELECT h.DISPATCH_NO, h.DISPATCH_DT, h.RQSHPD,
                   h.DPTNKY, h.DPTNM, h.CARTYPE, h.STATUS,
                   h.TOTAL_KG, h.TOTAL_CNT, h.NOTE, h.CREDAT,
                   COALESCE(v.LOAD_TON, 0) AS LOAD_TON
            FROM KNRAWMS.PS_DISPATCH_H h
            LEFT JOIN KNRAWMS.DS_VEHICLE v ON v.CARTYPE = h.CARTYPE
            WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();

        if (dateFrom != null && !dateFrom.isEmpty()) { sb.append(" AND h.RQSHPD >= ?"); params.add(dateFrom); }
        if (dateTo   != null && !dateTo.isEmpty())   { sb.append(" AND h.RQSHPD <= ?"); params.add(dateTo); }
        if (dptnky   != null && !dptnky.isEmpty())   {
            sb.append(" AND (h.DPTNKY LIKE ? OR h.DPTNM LIKE ?)");
            params.add("%" + dptnky + "%"); params.add("%" + dptnky + "%");
        }
        if (status     != null && !status.isEmpty())     { sb.append(" AND h.STATUS=?");        params.add(status); }
        if (dispatchNo != null && !dispatchNo.isEmpty()) { sb.append(" AND h.DISPATCH_NO LIKE ?"); params.add("%" + dispatchNo + "%"); }
        sb.append(" ORDER BY h.RQSHPD DESC, h.DISPATCH_NO");

        // MariaDB tmsEm으로 실행
        var q = tmsEm.createNativeQuery(sb.toString());
        for (int i = 0; i < params.size(); i++) q.setParameter(i + 1, params.get(i));

        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        List<PsDispatchListResponse> result = new ArrayList<>();

        for (Object[] r : rows) {
            String dno    = str(r[0]);
            double loadTon = toDouble(r[11]);
            Double loadKg  = loadTon > 0 ? Math.round(loadTon * 1000.0 * 10.0) / 10.0 : null;

            // ── Step 1: PS_DISPATCH_D → MariaDB tmsEm ──
            @SuppressWarnings("unchecked")
            List<Object[]> details = tmsEm.createNativeQuery("""
                SELECT d.ITEM_ID, d.SEQ, d.SHPOKY, d.SHPOIT, d.SKUKEY, d.DESC01,
                       d.QTSHPO, d.UOMKEY, d.DPTNKY, d.DPTNM,
                       d.IS_SPLIT, d.ORG_SHPOKY, d.ORG_SHPOIT,
                       COALESCE(d.GRSWGT,0), COALESCE(d.KG_WEIGHT,0)
                FROM KNRAWMS.PS_DISPATCH_D d
                WHERE d.DISPATCH_NO = ?
                ORDER BY d.SEQ
                """)
              .setParameter(1, dno)
              .getResultList();

            // ── Step 2: SKUKEY 목록으로 Oracle KNRAWMS.RECDI 별도 조회 ──
            Map<String, Double> recdiMap = new HashMap<>();
            if (!details.isEmpty()) {
                Set<String> skukeys = new LinkedHashSet<>();
                for (Object[] d : details) {
                    String sk = str(d[4]);
                    if (!sk.isEmpty()) skukeys.add(sk);
                }
                if (!skukeys.isEmpty()) {
                    String skPh = skukeys.stream().map(x -> "?").collect(Collectors.joining(","));
                    var recdiQ = em.createNativeQuery(
                        "SELECT SKUKEY, COALESCE(QTYRCV, 0) FROM KNRAWMS.RECDI WHERE SKUKEY IN (" + skPh + ")"
                    );
                    int pi = 1;
                    for (String sk : skukeys) recdiQ.setParameter(pi++, sk);

                    @SuppressWarnings("unchecked")
                    List<Object[]> recdiRows = recdiQ.getResultList();
                    for (Object[] rd : recdiRows) {
                        recdiMap.put(str(rd[0]), toDouble(rd[1]));
                    }
                }
            }

            // ── Step 3: 결과 조립 ──
            int totalRoll = 0;
            List<PsDispatchListResponse.ItemDetail> items = new ArrayList<>();
            for (Object[] d : details) {
                String sk  = str(d[4]);
                String uom = str(d[7]).strip();
                double kgW = toDouble(d[14]);
                double unitW = recdiMap.getOrDefault(sk, 0.0);

                if (psIsRoll(sk)) {
                    if ("R".equals(uom))        totalRoll += (int) toDouble(d[6]);
                    else if ("KG".equals(uom)) {
                        double singleW = unitW > 0 ? unitW : 600.0;
                        if (kgW > 0) totalRoll += (int) Math.ceil(kgW / singleW);
                    }
                }

                items.add(PsDispatchListResponse.ItemDetail.builder()
                    .itemId(toLong(d[0]))
                    .seq(toInt(d[1]))
                    .shpoky(str(d[2]))
                    .shpoit(str(d[3]))
                    .skukey(sk)
                    .desc01(str(d[5]))
                    .qtshpo(toDouble(d[6]))
                    .uomkey(uom)
                    .dptnky(str(d[8]))
                    .dptnm(str(d[9]))
                    .isSplit(toInt(d[10]))
                    .orgShpoky(str(d[11]))
                    .orgShpoit(str(d[12]))
                    .grswgt(toDouble(d[13]))
                    .kgWeight(kgW)
                    .unitWeight(unitW)
                    .build());
            }

            result.add(PsDispatchListResponse.builder()
                .dispatchNo(dno)
                .dispatchDt(str(r[1]))
                .rqshpd(str(r[2]))
                .dptnky(str(r[3]))
                .dptnm(str(r[4]))
                .cartype(str(r[5]))
                .status(str(r[6]))
                .totalKg(toDouble(r[7]))
                .totalCnt(toInt(r[8]))
                .note(str(r[9]))
                .credat(str(r[10]))
                .loadTon(loadTon)
                .loadKg(loadKg)
                .rollCount(totalRoll)
                .items(items)
                .build());
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 배차 확정 (Flask api_ps_dispatch_confirm)
    // PS_DISPATCH_H.STATUS 업데이트 → MariaDB tmsEm
    // ──────────────────────────────────────────────────────────────────────────
    @Transactional(transactionManager = "tmsTransactionManager")
    public int confirmDispatch(PsDispatchConfirmRequest req) {
        List<String> nos = req.getDispatchNos();
        String ph = nos.stream().map(x -> "?").collect(Collectors.joining(","));
        var q = tmsEm.createNativeQuery(
            "UPDATE KNRAWMS.PS_DISPATCH_H SET STATUS='CONFIRMED' WHERE DISPATCH_NO IN (" + ph + ")"
        );
        for (int i = 0; i < nos.size(); i++) q.setParameter(i + 1, nos.get(i));
        return q.executeUpdate();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 내부 유틸
    // ──────────────────────────────────────────────────────────────────────────
    private String str(Object o) {
        return o == null ? "" : o.toString().strip();
    }

    private double toDouble(Object o) {
        if (o == null) return 0.0;
        try { return Double.parseDouble(o.toString()); }
        catch (Exception e) { return 0.0; }
    }

    private int toInt(Object o) {
        if (o == null) return 0;
        try { return (int) Math.round(toDouble(o)); }
        catch (Exception e) { return 0; }
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        try { return Long.parseLong(o.toString()); }
        catch (Exception e) { return null; }
    }
}
