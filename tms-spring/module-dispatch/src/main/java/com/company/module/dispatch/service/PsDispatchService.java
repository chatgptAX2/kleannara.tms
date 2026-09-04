package com.company.module.dispatch.service;

import com.company.module.dispatch.dto.*;
import com.company.module.dispatch.entity.PsDispatchH;
import com.company.module.dispatch.entity.PsDispatchI;
import com.company.module.dispatch.repository.PsDispatchHRepository;
import com.company.module.dispatch.repository.PsDispatchIRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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

    // ★ 인치 판별 코드 — AutoDispatchService.INCH12/INCH3 및 프론트(PSOP_INCH12/PSOP_INCH3)와 동일하게 통일.
    //   과거 세트(s12/p12/...)에는 sr1 등 실제 원지 코드가 누락되어 인치 미표시 버그 발생 → 정본 세트로 교체.
    private static final Set<String> INCH12_CODES = Set.of(
        "a11","ab1","ag1","am1","111","s11","i11","k11","sm1",
        "s12","i12","k12","a12",
        "st1","su1","sh1","ks1","kc1");
    private static final Set<String> INCH3_CODES  = Set.of(
        "ar1","ae1","aj1","al1","sr1","ir1","rw1",
        "s72","i72","s32","s31","sp2","sz2","sc2",
        "sn1","sy2","b42","b41","s51","l41",
        "ra1","rp1","rs1","rg1");

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
    //
    // ■ 동적 WHERE 설계 (소프트파싱용 '? IS NULL OR ...' 고정조건 제거)
    //   기존에는 Shared Pool 재사용(소프트파싱)을 위해 모든 조건을
    //   (? IS NULL OR col ...) 형태로 항상 SQL 에 포함했으나,
    //   이 방식은 불필요한 OR 조건 평가로 옵티마이저가 인덱스를 제대로 타지 못해
    //   실제 조회 성능이 저하됐다.
    //   → 값이 존재하는 필터만 WHERE 절에 동적으로 추가하고, 값은 바인드 변수(?)로
    //     전달하여 실행계획이 실제 조건에 맞게 최적화되도록 한다.
    // ──────────────────────────────────────────────────────────────────────────

    // 납품문서 검색 SELECT/FROM/JOIN 베이스 (WHERE 절은 searchDocs 에서 동적 생성)
    private static final String SEARCH_DOCS_BASE_SQL =
        "SELECT i.SHPOKY, i.SHPOIT, i.SKUKEY, i.DESC01," +
        "       TRIM(COALESCE(i.SVBELN,'')) AS SVBELN," +
        "       i.UOMKEY, CAST(i.QTSHPO AS NUMBER(18,4)) AS QTSHPO," +
        "       TRIM(COALESCE(i.SKUG05,'')) AS SKUG05," +
        "       h.DPTNKY," +
        "       TRIM(COALESCE(b.NAME01,'')) AS DPTNM," +
        "       h.DOCDAT, h.RQSHPD, h.SHPMTY," +
        "       TRIM(COALESCE(c.CDESC1,'')) AS SHPMTY_NM," +
        "       COALESCE(m.GRSWGT, 0) AS GRSWGT," +
        "       TRIM(COALESCE(i.LOTA03,'')) AS LOTA03," +
        "       (SELECT COALESCE(MAX(rd.QTYRCV), 0)" +
        "        FROM KNRAWMS.RECDI rd" +
        "        WHERE rd.SKUKEY = i.SKUKEY) AS UNIT_WEIGHT," +
        "       TRIM(COALESCE(i.SPOSNR,'')) AS SPOSNR" +
        " FROM KNRAWMS.SHPDI i" +
        " JOIN KNRAWMS.SHPDH h ON i.SHPOKY = h.SHPOKY" +
        " LEFT JOIN KNRAWMS.BZPTN b ON b.PTNRKY = h.DPTNKY AND b.PTNRTY = 'CT'" +
        " LEFT JOIN KNRAWMS.CMCDV c ON c.CMCDKY = 'TASOTY' AND c.CMCDVL = h.SHPMTY" +
        " LEFT JOIN KNRAWMS.SKUMA m ON m.SKUKEY = i.SKUKEY";

    private static final String SEARCH_DOCS_ORDER_BY =
        " ORDER BY h.RQSHPD, h.DPTNKY, i.SHPOKY, i.SHPOIT";

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

        // ── 동적 WHERE 절 구성 (소프트파싱용 '? IS NULL OR ...' 고정조건 제거) ──
        //   값이 존재하는 필터만 WHERE 에 추가하여 불필요한 조건 평가를 없애고
        //   실행계획이 실제 조건에 맞게 최적화되도록 한다.
        //   wareky/skug05 는 기본값(1100/10)을 항상 적용(등가 조건)한다.
        String vWareky = (req.getWareky() != null && !req.getWareky().isBlank())
                         ? req.getWareky().strip() : "1100";
        String vSkug05 = (req.getSkug05() != null && !req.getSkug05().isBlank())
                         ? req.getSkug05().strip() : "10";

        // ── WAREKY / SKUG05 는 TRIM 비교(정규화)로 매칭한다. ──────────────────
        //   [개선] SAP 납품분할로 신규 생성된 납품문서(SHPDI)의 WAREKY/SKUG05 값에
        //   앞뒤 공백/포맷 차이가 있으면 정확일치(=)에서 걸러져 PS배차 조회에
        //   나타나지 않는 문제가 있었다(출고예정정보는 해당 조건이 없어 정상 조회).
        //   → TRIM 후 비교하여 공백 차이로 인한 누락을 방지한다.
        StringBuilder where = new StringBuilder(" WHERE TRIM(h.WAREKY) = ? AND TRIM(i.SKUG05) = ?");
        List<Object> params = new ArrayList<>();
        params.add(vWareky);
        params.add(vSkug05);

        if (dateFrom != null && !dateFrom.isEmpty()) {
            where.append(" AND h.RQSHPD >= ?");
            params.add(dateFrom);
        }
        if (dateTo != null && !dateTo.isEmpty()) {
            where.append(" AND h.RQSHPD <= ?");
            params.add(dateTo);
        }
        if (dptnky != null && !dptnky.isEmpty()) {
            where.append(" AND (h.DPTNKY LIKE ? OR b.NAME01 LIKE ?)");
            String like = "%" + dptnky + "%";
            params.add(like);
            params.add(like);
        }
        if (shpoky != null && !shpoky.isEmpty()) {
            // 납품문서 검색 조건: 기존 (SHPOKY LIKE %..% OR SVBELN LIKE %..%) 는
            // 선행 와일드카드로 인덱스를 타지 못해 성능이 느림 →
            // SVBELN(납품문서번호) 정확일치(=)로 변경하여 인덱스 사용 유도.
            // [개선] 분할문서 등 SVBELN 값에 앞뒤 공백이 섞여 있어도 매칭되도록 TRIM 비교.
            where.append(" AND TRIM(i.SVBELN) = ?");
            params.add(shpoky.trim());
        }
        if (shpmtyList != null && !shpmtyList.isEmpty()) {
            // shpmty IN (...) : 가변 개수를 플레이스홀더로 전개
            String ph = shpmtyList.stream().map(x -> "?").collect(Collectors.joining(","));
            where.append(" AND h.SHPMTY IN (").append(ph).append(")");
            params.addAll(shpmtyList);
        }

        String searchSql = SEARCH_DOCS_BASE_SQL + where + SEARCH_DOCS_ORDER_BY;

        log.info("[PsDispatch] searchDocs — wareky={}, skug05={}, dateFrom={}, dateTo={}," +
                 " dptnky={}, shpoky={}, shpmty={}, status={}",
                 vWareky, vSkug05, dateFrom, dateTo, dptnky, shpoky, shpmtyList, dispStat);

        // ── 동적 SQL 실행 (값 있는 필터만 WHERE 에 반영) ─────────────────────
        //   ※ 배차완료 키(dispatchedSet) 조회를 메인 검색 뒤로 이동:
        //     기존에는 SHPDI 전체(WAREKY/일자/문서 필터 없음)를 매번 풀스캔하여
        //     실제 검색결과가 2건이어도 17초 이상 소요됐음.
        //     → 메인 검색 결과의 SHPOKY 목록으로만 배차여부를 조회(인덱스 IN)하도록 변경.
        log.info("[PsDispatch] ==== SQL BEGIN ====\n{}", searchSql);
        log.info("[PsDispatch] params: {}", params);

        var query = em.createNativeQuery(searchSql);
        // JPA 위치 파라미터는 1-based
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        log.info("[PsDispatch] DB \uc870\ud68c \uacb0\uacfc: {}건 (dispStat={})", rows.size(), dispStat);

        // ── 배차완료 키(SHPOKY|SHPOIT) 조회: 검색결과의 SHPOKY 로만 스코프 ──
        //   (SHPDI 전체 풀스캔 방지 — 결과 문서번호로만 IN 조회)
        Set<String> dispatchedSet = loadDispatchedKeys(rows);
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
                .sposnr(str(r[17]))
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
                .isSplit(str(r[0]).contains("-S"))   // 분할문서 여부
                .build());
        }
        return result;
    }

    /**
     * 배차완료 키(SHPOKY|SHPOIT) 집합 조회.
     *
     * <p>기존에는 {@code KNRAWMS.SHPDI} 전체(WAREKY/일자/문서번호 필터 없음)를 매번 스캔하여
     * 배차완료 키 전체를 가져왔다. 이 때문에 실제 검색결과가 소량(예: 2건)이어도
     * 대용량 테이블 풀스캔으로 17초 이상 소요되는 성능 병목이 있었다.</p>
     *
     * <p>배차여부(isDisp)는 <b>검색결과 행에 대해서만</b> 필요하므로,
     * 검색결과의 SHPOKY(납품문서번호) 목록으로만 스코프하여 인덱스 IN 조회로 대체한다.
     * 검색결과가 없으면 조회 자체를 생략한다.</p>
     *
     * @param rows 메인 검색 결과 (r[0]=SHPOKY, r[1]=SHPOIT)
     * @return "SHPOKY|SHPOIT" 형식의 배차완료 키 집합
     */
    private Set<String> loadDispatchedKeys(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) return java.util.Collections.emptySet();

        // 검색결과에 등장한 SHPOKY(납품문서번호) 만 distinct 수집
        LinkedHashSet<String> shpokySet = new LinkedHashSet<>();
        for (Object[] r : rows) {
            String shpoky = str(r[0]);
            if (!shpoky.isEmpty()) shpokySet.add(shpoky);
        }
        if (shpokySet.isEmpty()) return java.util.Collections.emptySet();

        List<String> shpokys = new ArrayList<>(shpokySet);

        // ── Oracle IN 절 1,000개 제한(ORA-01795) 회피: 1,000개 단위로 청크 분할 조회 ──
        //   넓은 기간(예: 8월 1~21일) 조회 시 검색결과 문서 수(distinct SHPOKY)가
        //   1,000개를 초과할 수 있어, IN 절을 여러 번으로 나눠 실행하고 결과를 합친다.
        final int CHUNK = 1000;
        Set<String> keys = new HashSet<>();
        for (int from = 0; from < shpokys.size(); from += CHUNK) {
            List<String> chunk = shpokys.subList(from, Math.min(from + CHUNK, shpokys.size()));
            String ph = chunk.stream().map(x -> "?").collect(Collectors.joining(","));
            // 배차완료 판정 기준: 가선적번호(STDLNR) 부여 여부 (STATIT 무관).
            //   ※ saveDispatch 는 STATIT 조건 없이 STDLNR 을 갱신하는데, 판정만
            //     STATIT='NEW' 를 요구하면 STATIT 이 'NEW' 가 아닌 문서는 저장 후에도
            //     '미배차' 로 표시되는 불일치가 발생 → STATIT 조건 제거.
            String sql =
                "SELECT SHPOKY || '|' || SHPOIT" +
                " FROM KNRAWMS.SHPDI" +
                " WHERE STDLNR IS NOT NULL AND TRIM(STDLNR) <> ''" +
                "   AND SHPOKY IN (" + ph + ")";

            var q = em.createNativeQuery(sql);
            for (int i = 0; i < chunk.size(); i++) {
                q.setParameter(i + 1, chunk.get(i));
            }
            @SuppressWarnings("unchecked")
            List<String> chunkKeys = q.getResultList();
            keys.addAll(chunkKeys);
        }
        return keys;
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
            // DISPATCH_TYPE : PS(판매/이송) 배차 저장 시 'GI'(출고) 로 고정
            //   ─ 반품 배차(배차(반품) 탭)는 별도 저장 경로에서 'GR' 로 저장한다.
            tmsEm.createNativeQuery("""
                INSERT INTO KNRAWMS.PS_DISPATCH_H
                  (DISPATCH_NO, DISPATCH_DT, RQSHPD, DPTNKY, DPTNM,
                   CARTYPE, STATUS, TOTAL_KG, TOTAL_CNT, CREDAT, CREUSR, DISPATCH_TYPE)
                VALUES (?,?,?,?,?,?,'DRAFT',?,?,?,?,'GI')
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
                    // ── SHPOKY / SHPOIT 검증 및 보정 ──────────────────────────────
                    //  PS_DISPATCH_D.SHPOKY / SHPOIT 는 NOT NULL(Oracle).
                    //  프론트 키 대소문자 불일치 등으로 null 이 들어오면 ORA-01400 발생.
                    //  1) shpoky 가 비어있고 orgShpoky 가 있으면 원본 값으로 보정
                    //  2) 그래도 비어있으면 어떤 항목이 잘못됐는지 명시하여 예외 → 전체 롤백
                    String shpoky = firstNonBlank(it.getShpoky(), it.getOrgShpoky());
                    String shpoit = firstNonBlank(it.getShpoit(), it.getOrgShpoit());
                    if (isBlank(shpoky) || isBlank(shpoit)) {
                        throw new IllegalArgumentException(
                            "배차 항목에 납품문서번호(SHPOKY) 또는 품목순번(SHPOIT)이 없습니다. " +
                            "[차량=" + dispatchNo + ", SKUKEY=" + str(it.getSkukey()) +
                            ", SHPOKY=" + str(it.getShpoky()) + ", SHPOIT=" + str(it.getShpoit()) + "]");
                    }

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
                      .setParameter(3,  shpoky)
                      .setParameter(4,  shpoit)
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

                    // key[2] = SVBELN(SAP 납품문서번호) — STDLNR 매칭 3차 폴백용
                    shpdiKeys.add(new String[]{shpoky, shpoit, str(it.getSvbeln())});
                    shpokySet.add(shpoky);
                }
            }

            // SHPDI.STDLNR = DISPATCH_NO (Oracle KNRAWMS.SHPDI 업데이트)
            // ※ saveDispatch 는 @Transactional(transactionManager="tmsTransactionManager")
            //   컨텍스트에서 실행되므로, wmsPU(em)로 executeUpdate() 하면 해당 EM의
            //   트랜잭션이 없어 TransactionRequiredException 이 발생한다.
            //   SHPDI/SHPDH/PS_DISPATCH_* 는 모두 동일한 Oracle KNRAWMS DB에 존재하므로,
            //   활성 트랜잭션(tmsPU)에 속한 tmsEm 으로 갱신하여 단일 트랜잭션 일관성을 확보한다.
            // ※ 가선적 성공 시, 요청에 포함된 SHPDI(SHPOKY, SHPOIT) 행의 STDLNR 에
            //   가선적번호(dispatchNo)를 반드시 기록해야 한다.
            //   기존 'STATIT = ''NEW''' 조건은 이미 상태가 전이된 행의 STDLNR 갱신을
            //   누락시키므로 제거하고, 요청 키(SHPOKY+SHPOIT) 기준으로 갱신한다.
            int shpdiUpdated = 0;
            List<String[]> notMatched = new ArrayList<>();
            for (String[] key : shpdiKeys) {
                // 1차: 정확 매칭(SHPOKY + SHPOIT)
                int n = tmsEm.createNativeQuery("""
                    UPDATE KNRAWMS.SHPDI
                    SET STDLNR  = ?,
                        LMODAT  = TO_CHAR(SYSDATE, 'YYYYMMDD'),
                        LMOUSR  = 'WEB'
                    WHERE SHPOKY = ? AND SHPOIT = ?
                    """)
                  .setParameter(1, dispatchNo)
                  .setParameter(2, key[0])
                  .setParameter(3, key[1])
                  .executeUpdate();

                // 2차 폴백: 앞뒤 공백/좌측 0패딩 차이로 정확매칭 실패 시
                //   TRIM 및 숫자비교(SHPOIT)로 재시도 (SHPDI.SHPOIT 이 '0010' vs '10' 등
                //   포맷 차이가 있어도 동일 품목 순번을 안전하게 매칭)
                if (n == 0) {
                    n = tmsEm.createNativeQuery("""
                        UPDATE KNRAWMS.SHPDI
                        SET STDLNR  = ?,
                            LMODAT  = TO_CHAR(SYSDATE, 'YYYYMMDD'),
                            LMOUSR  = 'WEB'
                        WHERE TRIM(SHPOKY) = TRIM(?)
                          AND (TRIM(SHPOIT) = TRIM(?)
                               OR (REGEXP_LIKE(TRIM(SHPOIT), '^[0-9]+$')
                                   AND REGEXP_LIKE(TRIM(?), '^[0-9]+$')
                                   AND TO_NUMBER(TRIM(SHPOIT)) = TO_NUMBER(TRIM(?))))
                        """)
                      .setParameter(1, dispatchNo)
                      .setParameter(2, key[0])
                      .setParameter(3, key[1])
                      .setParameter(4, key[1])
                      .setParameter(5, key[1])
                      .executeUpdate();
                }

                // 3차 폴백: SHPOKY 매칭 실패 시 SVBELN(SAP 납품문서번호) 기준 매칭.
                //   [개선] SAP 납품분할로 신규 생성된 문서는 프론트가 보유한 SHPOKY 와
                //   SHPDI 실제 SHPOKY 가 어긋날 수 있으나(분할 반영 타이밍/키 포맷 차이),
                //   SVBELN + SHPOIT 는 동일하므로 이를 기준으로 STDLNR 을 부여한다.
                //   → 분할문서 배차저장이 "갱신 0건"으로 전체 롤백되던 문제를 방지.
                if (n == 0 && key.length > 2 && !isBlank(key[2])) {
                    n = tmsEm.createNativeQuery("""
                        UPDATE KNRAWMS.SHPDI
                        SET STDLNR  = ?,
                            LMODAT  = TO_CHAR(SYSDATE, 'YYYYMMDD'),
                            LMOUSR  = 'WEB'
                        WHERE TRIM(SVBELN) = TRIM(?)
                          AND (TRIM(SHPOIT) = TRIM(?)
                               OR (REGEXP_LIKE(TRIM(SHPOIT), '^[0-9]+$')
                                   AND REGEXP_LIKE(TRIM(?), '^[0-9]+$')
                                   AND TO_NUMBER(TRIM(SHPOIT)) = TO_NUMBER(TRIM(?))))
                        """)
                      .setParameter(1, dispatchNo)
                      .setParameter(2, key[2])
                      .setParameter(3, key[1])
                      .setParameter(4, key[1])
                      .setParameter(5, key[1])
                      .executeUpdate();
                    if (n > 0) {
                        log.info("[PsDispatch] saveDispatch STDLNR 3차 폴백(SVBELN) 매칭 성공 "
                                 + "svbeln={}, shpoit={} → {}건", key[2], key[1], n);
                    }
                }

                shpdiUpdated += n;
                if (n == 0) notMatched.add(key);
            }

            // 진단 로그: 저장 성공 toast 는 떴는데 미배차로 돌아오고 SAP선적탭에 안 뜨는
            //   증상의 원인 추적용 — SHPDI.STDLNR 실제 갱신 건수를 남긴다.
            log.info("[PsDispatch] saveDispatch dispatchNo={} 요청항목={} SHPDI.STDLNR 갱신={}건",
                     dispatchNo, shpdiKeys.size(), shpdiUpdated);
            if (!notMatched.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (String[] k : notMatched) {
                    sb.append("[SHPOKY=").append(k[0]).append(", SHPOIT=").append(k[1])
                      .append(", SVBELN=").append(k.length > 2 ? k[2] : "").append(']');
                }
                log.warn("[PsDispatch] saveDispatch dispatchNo={} SHPDI 매칭 실패(STDLNR 미부여) {}건 → {} "
                         + "(배차완료 표시/ SAP선적탭 조회가 누락될 수 있음)",
                         dispatchNo, notMatched.size(), sb);
            }

            // ★ 저장 무결성 강제: SHPDI.STDLNR 이 한 건도 갱신되지 않았다면(=가선적번호 미부여)
            //   PS_DISPATCH_H/D 만 저장되고 배차완료 판정/ SAP선적탭 조회의 유일 기준인
            //   STDLNR 이 비어 결국 "저장 성공 toast 후 미배차로 복귀" 불일치가 발생한다.
            //   → 이 경우 예외를 던져 전체 트랜잭션을 롤백하고, 프론트에 명확한 오류를 반환한다.
            //   (기존에는 로그만 남기고 커밋되어 조용히 유령 저장이 됐음)
            if (!shpdiKeys.isEmpty() && shpdiUpdated == 0) {
                StringBuilder keyInfo = new StringBuilder();
                for (String[] k : shpdiKeys) {
                    keyInfo.append("[SHPOKY=").append(k[0]).append(", SHPOIT=").append(k[1])
                           .append(", SVBELN=").append(k.length > 2 ? k[2] : "").append(']');
                }
                throw new IllegalStateException(
                    "배차저장 실패: SHPDI 가선적번호(STDLNR) 갱신 대상이 없습니다. " +
                    "납품문서 키(SHPOKY/SHPOIT/SVBELN)가 SHPDI 와 일치하지 않습니다. " +
                    "[dispatchNo=" + dispatchNo + ", 요청항목=" + shpdiKeys.size() + "건] " + keyInfo);
            }

            // ★ 저장 직후 자가검증(SELECT): 방금 채번한 dispatchNo 로 SHPDI 에서
            //   STDLNR 이 실제로 채워졌는지 tmsEm 으로 즉시 재조회하여 로그에 남긴다.
            //   (커밋 전 상태이지만 동일 트랜잭션 내라 반영 여부 확인 가능 →
            //    UPDATE 성공/실패 및 실제 반영 건수를 운영 로그로 100% 특정)
            try {
                Object verifyCnt = tmsEm.createNativeQuery(
                        "SELECT COUNT(*) FROM KNRAWMS.SHPDI WHERE STDLNR = ?")
                    .setParameter(1, dispatchNo)
                    .getSingleResult();
                log.info("[PsDispatch] saveDispatch 자가검증 — SHPDI.STDLNR='{}' 반영 행수={}",
                         dispatchNo, verifyCnt);
            } catch (Exception ve) {
                log.warn("[PsDispatch] saveDispatch 자가검증 SELECT 실패 dispatchNo={} : {}",
                         dispatchNo, ve.getMessage());
            }

            // SHPDH.VEHINO = carclass_cd (Oracle KNRAWMS.SHPDH 업데이트 → tmsEm, 위와 동일 사유)
            // ※ SHPDH 의 VEHINO/CARTON/CARNO/DRIVER/DRIVERCEL 컬럼은 Oracle 에서 NOT NULL 제약이
            //   걸려 있어 NULL 을 세팅하면 ORA-01407 이 발생한다.
            //   → NVL(?, ' ') / 리터럴 ' ' 로 NULL 을 공백 1칸으로 치환하여 제약 위반을 방지한다.
            if (!shpokySet.isEmpty()) {
                String ph = shpokySet.stream().map(x -> "?").collect(Collectors.joining(","));
                var q = tmsEm.createNativeQuery(
                    "UPDATE KNRAWMS.SHPDH SET VEHINO=NVL(?, ' '), CARTON=NVL(?, ' ')," +
                    " CARNO=' ', DRIVER=' ', DRIVERCEL=' '," +
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
    // 커밋 후 검증 — SAP선적탭 미조회 근본원인 진단/방어용
    //
    // saveDispatch 는 tmsEm(tmsPU, HikariPool-TMS) 트랜잭션에서 SHPDI.STDLNR 을
    // 갱신·커밋한다. SAP선적탭은 wmsJdbc/em(wmsPU, HikariPool-WMS) 로 SHPDI 를 읽는다.
    // 두 datasource 는 동일 물리 DB(KNMESWMS/KNRATMS)이므로, tms 트랜잭션이 정상
    // 커밋되었다면 wms 읽기경로에서도 STDLNR 이 반드시 보여야 한다.
    //
    // 이 메서드는 saveDispatch **커밋 완료 후** 별도 wms 읽기경로(em)로 STDLNR 반영
    // 건수를 재조회하여, "tms 에는 썼는데 wms 경로에는 0건" 상황(=커밋 미반영/격리
    // /가상 저장)을 운영 로그로 100% 특정한다.
    //
    // ※ REQUIRES_NEW + wmsTransactionManager 로 별도 트랜잭션을 열어, 방금 커밋된
    //   최신 상태를 wms 커넥션에서 조회한다.
    // ──────────────────────────────────────────────────────────────────────────
    @Transactional(transactionManager = "wmsTransactionManager",
                   propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
                   readOnly = true)
    public int verifyStdlnrViaWms(List<String> dispatchNos) {
        if (dispatchNos == null || dispatchNos.isEmpty()) return 0;
        int total = 0;
        for (String no : dispatchNos) {
            try {
                Object cnt = em.createNativeQuery(
                        "SELECT COUNT(*) FROM KNRAWMS.SHPDI WHERE STDLNR = ?")
                    .setParameter(1, no)
                    .getSingleResult();
                int n = cnt == null ? 0 : ((Number) cnt).intValue();
                total += n;
                if (n > 0) {
                    log.info("[PsDispatch] 커밋후검증(wms경로) — SHPDI.STDLNR='{}' 조회 {}건 (정상: SAP탭 노출 가능)", no, n);
                } else {
                    log.error("[PsDispatch] 커밋후검증(wms경로) — SHPDI.STDLNR='{}' 조회 0건! "
                            + "tms 트랜잭션이 커밋됐는데도 wms 읽기경로에 보이지 않음 → "
                            + "커밋 미반영/트랜잭션 롤백/서로 다른 물리DB 의심", no);
                }
            } catch (Exception e) {
                log.warn("[PsDispatch] 커밋후검증(wms경로) 실패 dispatchNo={} : {}", no, e.getMessage());
            }
        }
        return total;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 배차 목록 조회 (Flask api_ps_dispatch_list)
    // PS_DISPATCH_H + ds_vehicle → tmsEm
    //
    // ■ 동적 WHERE 설계 (소프트파싱용 '? IS NULL OR ...' 고정조건 제거)
    //   값이 존재하는 필터만 WHERE 절에 동적으로 추가한다.
    // ──────────────────────────────────────────────────────────────────────────

    private static final String GET_LIST_BASE_SQL =
        "SELECT h.DISPATCH_NO, h.DISPATCH_DT, h.RQSHPD," +
        "       h.DPTNKY, h.DPTNM, h.CARTYPE, h.STATUS," +
        "       h.TOTAL_KG, h.TOTAL_CNT, h.NOTE, h.CREDAT," +
        "       COALESCE(v.LOAD_TON, 0) AS LOAD_TON" +
        " FROM KNRAWMS.PS_DISPATCH_H h" +
        " LEFT JOIN KNRAWMS.DS_VEHICLE v ON v.CARTYPE = h.CARTYPE";

    private static final String GET_LIST_ORDER_BY =
        " ORDER BY h.RQSHPD DESC, h.DISPATCH_NO";

    public List<PsDispatchListResponse> getList(PsDispatchListRequest req) {
        String dateFrom   = req.normalizedDateFrom();
        String dateTo     = req.normalizedDateTo();
        String dptnky     = req.getDptnky();
        String status     = req.getStatus();
        String dispatchNo = req.getDispatchNo();

        // ── 동적 WHERE 절 구성 (값 있는 필터만 추가) ────────────────────────
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        if (dateFrom != null && !dateFrom.isEmpty()) {
            where.append(where.length() == 0 ? " WHERE" : " AND").append(" h.RQSHPD >= ?");
            params.add(dateFrom);
        }
        if (dateTo != null && !dateTo.isEmpty()) {
            where.append(where.length() == 0 ? " WHERE" : " AND").append(" h.RQSHPD <= ?");
            params.add(dateTo);
        }
        if (dptnky != null && !dptnky.isEmpty()) {
            where.append(where.length() == 0 ? " WHERE" : " AND").append(" (h.DPTNKY LIKE ? OR h.DPTNM LIKE ?)");
            String like = "%" + dptnky + "%";
            params.add(like); params.add(like);
        }
        if (status != null && !status.isEmpty()) {
            where.append(where.length() == 0 ? " WHERE" : " AND").append(" h.STATUS = ?");
            params.add(status);
        }
        if (dispatchNo != null && !dispatchNo.isEmpty()) {
            where.append(where.length() == 0 ? " WHERE" : " AND").append(" h.DISPATCH_NO LIKE ?");
            params.add("%" + dispatchNo + "%");
        }

        String listSql = GET_LIST_BASE_SQL + where + GET_LIST_ORDER_BY;
        var q = tmsEm.createNativeQuery(listSql);
        for (int i = 0; i < params.size(); i++) {
            q.setParameter(i + 1, params.get(i));
        }

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
                    // Oracle IN 절 1,000개 제한(ORA-01795) 회피: 1,000개 단위 청크 분할
                    final int CHUNK = 1000;
                    List<String> skList = new ArrayList<>(skukeys);
                    for (int from = 0; from < skList.size(); from += CHUNK) {
                        List<String> chunk = skList.subList(from, Math.min(from + CHUNK, skList.size()));
                        String skPh = chunk.stream().map(x -> "?").collect(Collectors.joining(","));
                        var recdiQ = em.createNativeQuery(
                            "SELECT SKUKEY, COALESCE(QTYRCV, 0) FROM KNRAWMS.RECDI WHERE SKUKEY IN (" + skPh + ")"
                        );
                        int pi = 1;
                        for (String sk : chunk) recdiQ.setParameter(pi++, sk);

                        @SuppressWarnings("unchecked")
                        List<Object[]> recdiRows = recdiQ.getResultList();
                        for (Object[] rd : recdiRows) {
                            recdiMap.put(str(rd[0]), toDouble(rd[1]));
                        }
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
    // 저장배차 불러오기 (편집용)
    // Flask: POST /api/ps-dispatch/load-for-edit
    //   입력 : { dispatch_nos: ["260728001T", ...] }
    //   출력 : { ok, count, vehicles:[...], search_rows:[...] }
    //
    // ※ 프론트(psopMergeRestoredVehicles / psopBuildVehicleCard)는 UPPERCASE 키를
    //    사용하므로 여기서 대문자 키 Map 으로 조립하여 반환한다.
    // ──────────────────────────────────────────────────────────────────────────
    public Map<String, Object> loadForEdit(List<String> dispatchNos) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> vehicles   = new ArrayList<>();
        List<Map<String, Object>> searchRows = new ArrayList<>();

        if (dispatchNos == null || dispatchNos.isEmpty()) {
            out.put("ok", true);
            out.put("count", 0);
            out.put("vehicles", vehicles);
            out.put("search_rows", searchRows);
            return out;
        }

        for (String no : dispatchNos) {
            if (no == null || no.isBlank()) continue;

            // getList() 재활용 — dispatchNo 정확 일치 조회
            PsDispatchListRequest req = new PsDispatchListRequest();
            req.setDispatchNo(no.strip());
            List<PsDispatchListResponse> found = getList(req);

            // getList 는 LIKE 검색이므로 정확히 일치하는 건만 선별
            PsDispatchListResponse h = found.stream()
                .filter(x -> no.strip().equals(x.getDispatchNo()))
                .findFirst().orElse(null);
            if (h == null) continue;

            // ── items → UPPERCASE 키 Map 리스트 ──
            List<Map<String, Object>> items = new ArrayList<>();
            if (h.getItems() != null) {
                for (PsDispatchListResponse.ItemDetail it : h.getItems()) {
                    Map<String, Object> im = new LinkedHashMap<>();
                    im.put("SHPOKY",     it.getShpoky());
                    im.put("SHPOIT",     it.getShpoit());
                    im.put("SKUKEY",     it.getSkukey());
                    im.put("DESC01",     it.getDesc01());
                    im.put("QTSHPO",     it.getQtshpo());
                    im.put("UOMKEY",     it.getUomkey());
                    im.put("DPTNKY",     it.getDptnky());
                    im.put("DPTNM",      it.getDptnm());
                    im.put("IS_SPLIT",   it.getIsSplit());
                    im.put("ORG_SHPOKY", it.getOrgShpoky());
                    im.put("ORG_SHPOIT", it.getOrgShpoit());
                    im.put("GRSWGT",     it.getGrswgt());
                    im.put("KG_WEIGHT",  it.getKgWeight());
                    im.put("UNIT_WEIGHT",it.getUnitWeight());
                    items.add(im);

                    // search_rows (미배차 문서 테이블 동기화용)
                    Map<String, Object> sr = new LinkedHashMap<>();
                    sr.put("SHPOKY",     it.getShpoky());
                    sr.put("SHPOIT",     it.getShpoit());
                    sr.put("SKUKEY",     it.getSkukey());
                    sr.put("DESC01",     it.getDesc01());
                    sr.put("QTSHPO",     it.getQtshpo());
                    sr.put("UOMKEY",     it.getUomkey());
                    sr.put("DPTNKY",     it.getDptnky());
                    sr.put("DPTNM",      it.getDptnm());
                    sr.put("KG_WEIGHT",  it.getKgWeight());
                    sr.put("DISPATCHED", true);
                    searchRows.add(sr);
                }
            }

            // ── vehicle 헤더 → UPPERCASE 키 Map ──
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("DISPATCH_NO", h.getDispatchNo());
            v.put("cartype",     h.getCartype());
            v.put("carclass_cd", "");
            v.put("rqshpd",      h.getRqshpd());
            v.put("dptnky",      h.getDptnky());
            v.put("dptnm",       h.getDptnm());
            v.put("total_kg",    h.getTotalKg());
            v.put("total_cnt",   h.getTotalCnt());
            v.put("load_kg",     h.getLoadKg());
            v.put("status",      h.getStatus());
            v.put("items",       items);
            vehicles.add(v);
        }

        out.put("ok", true);
        out.put("count", vehicles.size());
        out.put("vehicles", vehicles);
        out.put("search_rows", searchRows);
        return out;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 내부 유틸
    // ──────────────────────────────────────────────────────────────────────────
    private String str(Object o) {
        return o == null ? "" : o.toString().strip();
    }

    /** null 또는 공백 문자열 여부 */
    private boolean isBlank(String s) {
        return s == null || s.strip().isEmpty();
    }

    /** 앞에서부터 처음으로 non-blank 인 값을 반환(모두 blank 면 null) */
    private String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (!isBlank(v)) return v.strip();
        }
        return null;
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
