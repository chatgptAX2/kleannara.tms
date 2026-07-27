package com.company.module.wms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 자동배차 알고리즘 Service
 *
 * ■ DataSource 라우팅
 *   - wmsJdbc (Oracle KNRAWMS): SKUMA, SHPDH, SHPDI, BZPTN, BZPTN_DETAIL, CMCDV
 *   - tmsJdbc (Oracle KNRAWMS): DS_VEHICLE, DS_INCH12, DS_INCH3,
 *                                    DS_DISPATCH_PROFILE, DS_DISPATCH_CONST, ROUTE_COST
 *
 *   ※ Cross-DB 조인(KNRAWMS.CMCDV ↔ DS_VEHICLE 등) 불가 → 2-step 분리
 */
@Slf4j
@Service
public class AutoDispatchService {

    /** Oracle KNRAWMS 전용 JdbcTemplate */
    private final JdbcTemplate wmsJdbc;
    /** Oracle KNRAWMS tmsJdbc — TMS 테이블 전용 JdbcTemplate */
    private final JdbcTemplate tmsJdbc;

    public AutoDispatchService(
            @Qualifier("wmsJdbcTemplate") JdbcTemplate wmsJdbc,
            @Qualifier("tmsJdbcTemplate") JdbcTemplate tmsJdbc) {
        this.wmsJdbc = wmsJdbc;
        this.tmsJdbc = tmsJdbc;
    }

    // ── 인치 코드 상수 (Flask PS_INCH12_CODES / PS_INCH3_CODES) ──────
    private static final Set<String> INCH12 = new HashSet<>(Arrays.asList(
        "a11","ab1","ag1","am1","111","s11","i11","k11","sm1",
        "s12","i12","k12","a12",
        "st1","su1","sh1","ks1","kc1"
    ));
    private static final Set<String> INCH3 = new HashSet<>(Arrays.asList(
        "ar1","ae1","aj1","al1","sr1","ir1","rw1",
        "s72","i72","s32","s31","sp2","sz2","sc2",
        "sn1","sy2","b42","b41","s51","l41",
        "ra1","rp1","rs1","rg1"
    ));

    private static final double PAPER_DENSITY = 1200.0;   // kg/m³ (코팅지)
    private static final double PAPER_DENSITY_G_PER_MM3 = 0.0012;

    // ════════════════════════════════════════════════════════════════
    //  진입점: /api/dispatch-constraint/auto
    // ════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public Map<String, Object> runAuto(Map<String, Object> body) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        Integer profileId = toInt(body.get("profile_id"));
        String dateStr    = str(body.get("date"));
        String ptnrkyFilter = str(body.get("ptnrky"));

        // 날짜/납품처 기반 자동 아이템 조회
        if ((items == null || items.isEmpty()) && !dateStr.isEmpty()) {
            items = fetchItemsByDate(dateStr.replace("-",""), ptnrkyFilter);
        }
        if (items == null || items.isEmpty()) {
            return Map.of("ok", false, "error", "items 없음 — 해당 날짜의 출고 데이터가 없습니다");
        }

        // 프로파일 로드
        Map<String, Object> prof = loadProfile(profileId);
        if (prof == null) return Map.of("ok", false, "error", "활성 프로파일 없음");

        String objective = str(prof.getOrDefault("OBJECTIVE", "MIN_VEHICLES"));
        long pid         = toLong(prof.get("PROFILE_ID"), 0L);

        // ── 제약조건 세트(DS_DISPATCH_CONST_SET_ITEM) 로드 ─────────────
        // 프로파일에 SET_ID가 연결되어 있으면 세트 아이템을 로드하여 제약조건 값 오버라이드/비활성 필터링에 사용
        Integer setId = toInt(prof.get("SET_ID"));
        String appliedSetNm = "";
        // CONST_ID → {ACTIVE_YN, PARAM_VALUE} 맵
        Map<Long, Map<String, Object>> setItemMap = new LinkedHashMap<>();
        if (setId != null) {
            try {
                List<Map<String, Object>> setItems = tmsJdbc.queryForList(
                    "SELECT i.ITEM_ID, i.CONST_ID, i.ACTIVE_YN, i.PARAM_VALUE, s.SET_NM" +
                    " FROM KNRAWMS.DS_DISPATCH_CONST_SET_ITEM i" +
                    " JOIN KNRAWMS.DS_DISPATCH_CONST_SET s ON s.SET_ID = i.SET_ID" +
                    " WHERE i.SET_ID = ?",
                    setId
                );
                for (Map<String, Object> si : setItems) {
                    long constId = toLong(si.get("CONST_ID"), -1L);
                    if (constId >= 0) setItemMap.put(constId, si);
                    if (appliedSetNm.isEmpty()) appliedSetNm = str(si.get("SET_NM"));
                }
                log.info("[AutoDispatch] 적용 세트: SET_ID={}, SET_NM={}, 아이템 수={}",
                         setId, appliedSetNm, setItemMap.size());
            } catch (Exception ex) {
                log.warn("[AutoDispatch] 세트 아이템 로드 실패 (SET_ID={}) — 마스터 기준으로 진행: {}", setId, ex.getMessage());
            }
        }

        // 제약 조건 로드 (DS_DISPATCH_CONST)
        List<Map<String, Object>> constRows = tmsJdbc.queryForList(
            "SELECT * FROM KNRAWMS.DS_DISPATCH_CONST WHERE PROFILE_ID=? AND ACTIVE_YN='Y' ORDER BY SORT_SEQ",
            pid
        );
        // 전역 제약 맵
        Map<String, Map<String, Object>> C = new LinkedHashMap<>();
        Map<String, Map<String, Map<String, Object>>> Cbt = new HashMap<>();  // TARGET_ID별
        Set<String> allowedCartypes = new HashSet<>();

        for (Map<String, Object> r : constRows) {
            long constId = toLong(r.get("CONST_ID"), -1L);

            // ── 세트 오버라이드 적용 ──────────────────────────────────
            if (!setItemMap.isEmpty() && setItemMap.containsKey(constId)) {
                Map<String, Object> si = setItemMap.get(constId);
                // 세트에서 비활성(N) 처리된 항목 → skip
                if ("N".equalsIgnoreCase(str(si.get("ACTIVE_YN")))) continue;
                // 세트의 PARAM_VALUE가 존재하면 CONST_VALUE를 오버라이드
                String paramVal = str(si.get("PARAM_VALUE"));
                if (!paramVal.isEmpty()) {
                    r = new HashMap<>(r);
                    r.put("CONST_VALUE", paramVal);
                }
            }
            // ─────────────────────────────────────────────────────────

            String key = str(r.get("CONST_KEY"));
            String tid = str(r.get("TARGET_ID"));
            if (!tid.isEmpty()) {
                Cbt.computeIfAbsent(tid, k -> new HashMap<>()).put(key, r);
                if ("VEHICLE".equals(str(r.get("CONST_TYPE")))
                    && "ALLOW_CARTYPE".equals(key)
                    && "Y".equals(str(r.getOrDefault("CONST_VALUE","Y")))) {
                    allowedCartypes.add(tid);
                }
            } else {
                C.put(key, r);
            }
        }

        ConstraintParams cp = buildConstraintParams(C);
        log.info("[AutoDispatch] 적용 제약 요약 — objective={}, entryTonLimit={}t, fixedVehPriority={}, " +
                 "maxVehPerGroup={}, roll3dCheck={}, board3dCheck={}, boardCbmCheck={}, " +
                 "boardMaxTonRatio={}, boardMaxCbmRatio={}, rollHeightMarginM={}, rollPalletApply={}({}m)",
                 objective, cp.entryTonLimit, cp.fixedVehPriority, cp.maxVehPerGroup,
                 cp.roll3dCheck, cp.board3dCheck, cp.boardCbmCheck,
                 cp.boardMaxTonRatio, cp.boardMaxCbmRatio, cp.rollHeightMarginM,
                 cp.rollPalletApply, cp.rollPalletDeductM);

        // 차량 마스터 로드
        List<Map<String, Object>> carOrder = loadCarOrder();
        Map<String, VehInfo>       vehInfo  = loadVehInfo();
        InchMaps                   inchMaps = loadInchMaps();

        // SKUMA 로드
        Map<String, SkuInfo> skumaMap = loadSkumaMap();

        // ROUTE_COST 로드 (MIN_COST)
        String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String costDate = "TODAY".equals(cval(C,"COST_REF_DATE","TODAY")) ? todayStr
                           : cval(C,"COST_REF_DATE","TODAY");
        Map<String, Map<String, Double>> routeCostMap = loadRouteCostMap(costDate);

        // 납품처별 TMS 정보 (BZPTN_DETAIL)
        List<String> allDptnky = items.stream()
            .map(it -> str(it.get("DPTNKY"))).filter(s -> !s.isEmpty())
            .distinct().collect(Collectors.toList());
        Map<String, PtnrInfo> ptnrInfoMap = loadPtnrInfo(allDptnky, vehInfo);

        // ── 우편번호 앞 3자리 혼적 그룹 또는 납품처 단위 그룹핑 ──────
        Map<String, List<Map<String, Object>>> groups = buildGroups(items, cp.allowMixedLoad);

        List<Map<String, Object>> allVehicles = new ArrayList<>();

        // ── 각 납품처 그룹 처리 ────────────────────────────────────────
        for (Map.Entry<String, List<Map<String, Object>>> entry : groups.entrySet()) {
            String groupKey = entry.getKey();
            List<Map<String, Object>> grpItems = entry.getValue();

            // 그룹 처리 전 차량 수 기록 (MAX_VEHICLES_PER_GROUP 검증용)
            int vehCountBefore = allVehicles.size();

            boolean isMixedGroup = cp.allowMixedLoad && groupKey.startsWith("_ZIP_");
            String dptnky, dptnm;
            List<Map<String, Object>> validCars;

            if (isMixedGroup) {
                // 혼적 그룹: 납품처 목록 추출 → 유효차 교집합
                List<String> mixedDks = grpItems.stream()
                    .map(it -> str(it.get("DPTNKY"))).distinct().collect(Collectors.toList());
                List<String> mixedDms = mixedDks.stream()
                    .map(dk -> grpItems.stream()
                        .filter(it -> dk.equals(str(it.get("DPTNKY"))))
                        .map(it -> str(it.get("DPTNM"))).findFirst().orElse(dk))
                    .collect(Collectors.toList());
                dptnky = mixedDks.isEmpty() ? "" : mixedDks.get(0);
                dptnm  = "혼적(" + String.join("/", mixedDms) + ")";

                // 교집합 유효 차량
                Set<String> commonTypes = null;
                List<Map<String, Object>> firstValidCars = null;
                for (String dk : mixedDks) {
                    List<Map<String, Object>> vc = getValidCars(dk, carOrder, vehInfo,
                        allowedCartypes, ptnrInfoMap, Cbt, cp);
                    Set<String> cts = vc.stream()
                        .map(c -> str(c.get("CARTYPE"))).collect(Collectors.toSet());
                    if (commonTypes == null) { commonTypes = cts; firstValidCars = vc; }
                    else commonTypes.retainAll(cts);
                }
                Set<String> finalCommon = commonTypes == null ? new HashSet<>() : commonTypes;
                validCars = firstValidCars == null ? new ArrayList<>()
                    : firstValidCars.stream()
                        .filter(c -> finalCommon.contains(str(c.get("CARTYPE"))))
                        .collect(Collectors.toList());
            } else {
                String[] parts = groupKey.split("\\|", -1);
                dptnky = parts.length > 0 ? parts[0] : "";
                dptnm  = parts.length > 1 ? parts[1] : "";
                validCars = getValidCars(dptnky, carOrder, vehInfo, allowedCartypes, ptnrInfoMap, Cbt, cp);
            }

            if (validCars.isEmpty()) {
                validCars = carOrder.stream()
                    .filter(c -> vehInfo.getOrDefault(str(c.get("CARTYPE")), VehInfo.EMPTY).loadKg > 0)
                    .collect(Collectors.toList());
            }

            String rqshpd = grpItems.isEmpty() ? "" : str(grpItems.get(0).get("RQSHPD"));
            PtnrInfo pi   = ptnrInfoMap.getOrDefault(dptnky, PtnrInfo.EMPTY);
            boolean isDynBlocked = "N".equals(pi.dynamicYn);

            // 원지 / 판지 / 기타 분류
            List<Map<String, Object>> rollItems  = grpItems.stream().filter(it -> isRollItem(it)).collect(Collectors.toList());
            List<Map<String, Object>> boardItems = grpItems.stream().filter(it -> isBoardItem(it)).collect(Collectors.toList());
            List<Map<String, Object>> otherItems = grpItems.stream()
                .filter(it -> !isRollItem(it) && !isBoardItem(it)).collect(Collectors.toList());

            boolean isMixedLoad = !rollItems.isEmpty() && !boardItems.isEmpty();

            // ── 원지 배차 (FFD/BFD BinPacking) ───────────────────
            if (!rollItems.isEmpty()) {
                processRollItems(rollItems, dptnky, dptnm, rqshpd, validCars, vehInfo,
                    carOrder, inchMaps, skumaMap, routeCostMap, ptnrInfoMap,
                    objective, cp, pid, prof, isMixedGroup, isMixedLoad, isDynBlocked,
                    pi, allVehicles);
            }

            // ── 판지 배차 (CBM + 중량 이중 한계) ─────────────────
            if (!boardItems.isEmpty()) {
                processBoardItems(boardItems, dptnky, dptnm, rqshpd, validCars, vehInfo,
                    carOrder, skumaMap, routeCostMap, ptnrInfoMap,
                    objective, cp, pid, prof, isMixedGroup, isMixedLoad, isDynBlocked,
                    pi, allVehicles);
            }

            // ── 기타 품목 배차 ────────────────────────────────────
            if (!otherItems.isEmpty()) {
                processOtherItems(otherItems, dptnky, dptnm, rqshpd, validCars, vehInfo,
                    routeCostMap, objective, cp, pid, prof, isDynBlocked, pi, allVehicles);
            }

            // ── 그룹당 최대 배차 차량 수(MAX_VEHICLES_PER_GROUP) 검증 ──
            int vehCountThisGroup = allVehicles.size() - vehCountBefore;
            if (cp.maxVehPerGroup > 0 && vehCountThisGroup > cp.maxVehPerGroup) {
                String warn = String.format(
                    "[그룹차량수초과] %s: 배차 차량 %d대 > 제한 %d대(MAX_VEHICLES_PER_GROUP) — 수동 검토 필요",
                    dptnm.isEmpty() ? dptnky : dptnm, vehCountThisGroup, cp.maxVehPerGroup);
                log.warn("[AutoDispatch] {}", warn);
                // 이 그룹에 속한 차량들에 경고 노트 부착
                for (int vi = vehCountBefore; vi < allVehicles.size(); vi++) {
                    Object notesObj = allVehicles.get(vi).get("notes");
                    if (notesObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> nlist = (List<String>) notesObj;
                        nlist.add(warn);
                    }
                }
            }
        }

        // 응답 필드 정규화
        for (Map<String, Object> v : allVehicles) {
            v.put("used_kg",   v.getOrDefault("total_kg", 0));
            v.put("load_ton",  round2(toLong(v.get("load_cap"), 0L) / 1000.0));
            v.put("cartype_nm", v.getOrDefault("cartype", ""));
            v.put("cost",      v.getOrDefault("route_cost", 0));
            v.computeIfAbsent("dynamic_yn",  k -> "");
            v.computeIfAbsent("mixed_dptnm", k -> "");
            v.computeIfAbsent("total_cbm",   k -> 0.0);
            v.computeIfAbsent("cbm_cap",     k -> 0.0);
            v.computeIfAbsent("cbm_fill",    k -> 0.0);
        }

        // 요약 통계
        double totalCost = allVehicles.stream()
            .mapToDouble(v -> dbl(v.get("route_cost"))).sum();
        double avgFill = allVehicles.isEmpty() ? 0.0
            : allVehicles.stream().mapToDouble(v -> dbl(v.get("fill_ratio"))).average().orElse(0.0);
        long dynBlockedCnt = allVehicles.stream()
            .filter(v -> "N".equals(str(v.get("dynamic_yn")))).count();
        double totalCbm = allVehicles.stream()
            .mapToDouble(v -> dbl(v.get("total_cbm"))).sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok",                  true);
        result.put("objective",           objective);
        result.put("profile_id",          pid);
        result.put("profile_nm",          str(prof.get("PROFILE_NM")));
        result.put("applied_set_id",      setId);
        result.put("applied_set_nm",      appliedSetNm.isEmpty() ? null : appliedSetNm);

        // 실제 적용된 제약조건 요약 (프론트/사용자 확인용)
        Map<String, Object> appliedConstraints = new LinkedHashMap<>();
        appliedConstraints.put("ENTRY_TON_LIMIT",       cp.entryTonLimit);
        appliedConstraints.put("FIXED_VEH_PRIORITY",    cp.fixedVehPriority);
        appliedConstraints.put("MAX_VEHICLES_PER_GROUP", cp.maxVehPerGroup);
        appliedConstraints.put("MIN_FILL_RATIO",        round2(cp.minFill * 100));
        appliedConstraints.put("MAX_FILL_RATIO",        round2(cp.maxFill * 100));
        appliedConstraints.put("MAX_ROLL_STACK_TIER",   cp.maxStack);
        appliedConstraints.put("MAX_BOARD_HEIGHT_M",    cp.maxBoardHeightM);
        appliedConstraints.put("ROLL_3D_CHECK_YN",      cp.roll3dCheck);
        appliedConstraints.put("BOARD_3D_CHECK_YN",     cp.board3dCheck);
        appliedConstraints.put("BOARD_CBM_CHECK_YN",    cp.boardCbmCheck);
        appliedConstraints.put("BOARD_MAX_TON_RATIO",   round2(cp.boardMaxTonRatio * 100));
        appliedConstraints.put("BOARD_MAX_CBM_RATIO",   round2(cp.boardMaxCbmRatio * 100));
        appliedConstraints.put("ROLL_3D_DEAD_SPACE_PCT",  cp.rollDeadSpacePct);
        appliedConstraints.put("BOARD_3D_DEAD_SPACE_PCT", cp.boardDeadSpacePct);
        appliedConstraints.put("ROLL_HEIGHT_MARGIN_M",  cp.rollHeightMarginM);
        appliedConstraints.put("ROLL_PALLET_APPLY_YN",  cp.rollPalletApply);
        appliedConstraints.put("ROLL_PALLET_DEDUCT_M",  cp.rollPalletDeductM);
        appliedConstraints.put("ALLOW_SPLIT_ITEM",      cp.allowSplit);
        appliedConstraints.put("ALLOW_MIXED_LOAD",      cp.allowMixedLoad);
        result.put("applied_constraints", appliedConstraints);

        result.put("total_vehicles",      allVehicles.size());
        result.put("total_cost",          Math.round(totalCost));
        result.put("avg_fill_ratio",      round2(avgFill));
        result.put("total_cbm",           round4(totalCbm));
        result.put("dynamic_blocked_cnt", dynBlockedCnt);
        result.put("dynamic_ok_cnt",      (long) allVehicles.size() - dynBlockedCnt);
        result.put("vehicles",            allVehicles);
        return result;
    }

    // ════════════════════════════════════════════════════════════════
    //  원지 배차 (FFD/BFD BinPacking)
    // ════════════════════════════════════════════════════════════════

    private void processRollItems(
        List<Map<String, Object>> rollItems,
        String dptnky, String dptnm, String rqshpd,
        List<Map<String, Object>> validCars,
        Map<String, VehInfo> vehInfo,
        List<Map<String, Object>> carOrder,
        InchMaps inchMaps,
        Map<String, SkuInfo> skumaMap,
        Map<String, Map<String, Double>> routeCostMap,
        Map<String, PtnrInfo> ptnrInfoMap,
        String objective, ConstraintParams cp,
        long pid, Map<String, Object> prof,
        boolean isMixedGroup, boolean isMixedLoad,
        boolean isDynBlocked, PtnrInfo pi,
        List<Map<String, Object>> allVehicles
    ) {
        String bigCar = validCars.isEmpty() ? "판별불가" : str(validCars.get(0).get("CARTYPE"));
        double bigCap = vehInfo.getOrDefault(bigCar, VehInfo.EMPTY).loadKg;
        if (bigCap <= 0) bigCap = 99_999_999.0;
        Map<String, Integer> bI12 = inchMaps.inch12.getOrDefault(bigCar, Collections.emptyMap());
        Map<String, Integer> bI3  = inchMaps.inch3.getOrDefault(bigCar, Collections.emptyMap());

        // ALLOW_SPLIT 사전 분할
        List<Map<String, Object>> splitItems = new ArrayList<>();
        List<String> splitNotesPre = new ArrayList<>();

        for (Map<String, Object> it : rollItems) {
            String sk    = str(it.get("SKUKEY"));
            String uom   = str(it.get("UOMKEY"));
            String inch  = getInch(sk);
            String grm   = getGrm(sk);
            int maxRc    = (inch.equals("12인치") ? bI12 : bI3).getOrDefault(grm, 0);

            if (cp.allowSplit && "R".equals(uom) && isRoll(sk)) {
                int totalRolls = (int) dbl(it.get("QTSHPO"));
                double totalKgIt = itemRollKg(it, skumaMap, cp.rollSingleKg);
                double perRollKg = totalRolls > 0 ? totalKgIt / totalRolls : cp.rollSingleKg;
                int rollsByKg    = perRollKg > 0 ? (int)(bigCap / perRollKg) : totalRolls;
                int chunkRolls   = Math.min(Math.min(maxRc > 0 ? maxRc : totalRolls, rollsByKg), totalRolls);

                if (chunkRolls > 0 && chunkRolls < totalRolls) {
                    int remain = totalRolls, idx = 1;
                    while (remain > 0) {
                        int cr = Math.min(chunkRolls, remain);
                        double ckKg = round4(cr * perRollKg);
                        Map<String, Object> chunk = new HashMap<>(it);
                        chunk.put("QTSHPO", cr);
                        chunk.put("KG_WEIGHT", ckKg);
                        chunk.put("_SPLIT_FROM", it.get("SHPOIT"));
                        chunk.put("_SPLIT_IDX",  idx);
                        splitItems.add(chunk);
                        remain -= cr; idx++;
                    }
                    splitNotesPre.add("[납품분할] " + str(it.get("SHPOKY")) + "#" + str(it.get("SHPOIT")));
                    continue;
                }
            }
            // KG_WEIGHT 미설정 시 계산
            Map<String, Object> itc = new HashMap<>(it);
            if (dbl(itc.get("KG_WEIGHT")) <= 0) itc.put("KG_WEIGHT", itemRollKg(it, skumaMap, cp.rollSingleKg));
            splitItems.add(itc);
        }

        // 목적식별 정렬
        if ("MAX_FILL".equals(objective)) {
            splitItems.sort(Comparator.comparingDouble(x -> itemRollKg(x, skumaMap, cp.rollSingleKg)));
        } else {
            splitItems.sort(Comparator.comparingDouble((Map<String, Object> x) -> itemRollKg(x, skumaMap, cp.rollSingleKg)).reversed());
        }

        // FFD / BFD BinPacking
        List<RollBin> bins = new ArrayList<>();
        for (Map<String, Object> it : splitItems) {
            double qtyKg = itemRollKg(it, skumaMap, cp.rollSingleKg);
            String inch  = getInch(str(it.get("SKUKEY")));
            String grm   = getGrm(str(it.get("SKUKEY")));
            int rc       = itemRollCount(it, skumaMap, cp.rollSingleKg);

            boolean placed = false;
            List<RollBin> searchBins = bins;
            if ("MAX_FILL".equals(objective)) {
                final double cap = bigCap;  // lambda capture용 final 복사
                searchBins = bins.stream()
                    .sorted(Comparator.comparingDouble(b -> cap - b.totalKg))
                    .collect(Collectors.toList());
            }

            for (RollBin b : searchBins) {
                if (canFitRollBin(b, qtyKg, inch, grm, rc, bigCap, bI12, bI3)) {
                    b.items.add(it); b.totalKg += qtyKg;
                    if ("12인치".equals(inch)) b.v12.merge(grm, rc, Integer::sum);
                    else if ("3인치".equals(inch)) b.v3.merge(grm, rc, Integer::sum);
                    placed = true; break;
                }
            }
            if (!placed) {
                RollBin nb = new RollBin();
                nb.items.add(it); nb.totalKg = qtyKg;
                if ("12인치".equals(inch)) nb.v12.put(grm, rc);
                else if ("3인치".equals(inch)) nb.v3.put(grm, rc);
                bins.add(nb);
            }
        }

        for (RollBin b : bins) {
            double vehKg = b.totalKg;

            // 인치 기준 최소 차량
            Map<String, Integer> v12r = new HashMap<>(), v3r = new HashMap<>();
            for (Map<String, Object> it : b.items) {
                String inch = getInch(str(it.get("SKUKEY")));
                String grm  = getGrm(str(it.get("SKUKEY")));
                int rc = itemRollCount(it, skumaMap, cp.rollSingleKg);
                if ("12인치".equals(inch)) v12r.merge(grm, rc, Integer::sum);
                else if ("3인치".equals(inch)) v3r.merge(grm, rc, Integer::sum);
            }
            List<String> cands = new ArrayList<>();
            v12r.forEach((g, rc) -> cands.add(findCar(inchMaps.inch12, g, rc, carOrder, vehInfo)));
            v3r.forEach( (g, rc) -> cands.add(findCar(inchMaps.inch3,  g, rc, carOrder, vehInfo)));

            String inchCar   = cands.isEmpty() ? bigCar
                : cands.stream().min(Comparator.comparingInt(ct -> sortKey(ct, carOrder))).orElse(bigCar);
            String costCar   = selectCar(vehKg, validCars, vehInfo, dptnky, objective, cp, routeCostMap);
            String vehCar    = sortKey(inchCar, carOrder) <= sortKey(costCar, carOrder) ? inchCar : costCar;

            // 높이 검사 + 차량 업그레이드 (기존 단순 높이 검사)
            List<String> notes = new ArrayList<>(splitNotesPre);
            if (isDynBlocked) notes.add("[동적배차불가] DYNAMIC_YN=N → 고정노선 전용 오더");
            else if ("Y".equals(pi.dynamicYn)) notes.add("[동적배차가능] DYNAMIC_YN=Y");

            for (Map<String, Object> it : b.items) {
                String sk = str(it.get("SKUKEY"));
                if (!isRoll(sk)) continue;
                Double dmm = calcRollDiameter(cp.rollSingleKg, sk);
                if (dmm == null) continue;

                int    actualStack = Math.min(cp.maxStack, 3);
                // 높이 안전 여유 마진(ROLL_HEIGHT_MARGIN_M) 반영: 적재 높이에 마진 가산
                double stackH      = dmm / 1000.0 * actualStack + cp.rollHeightMarginM;
                double effH        = rollEffH(vehCar, pi.forkliftYn, vehInfo, cp);

                if (stackH > effH) {
                    boolean upgraded = false;
                    for (Map<String, Object> c : carOrder) {
                        String ct = str(c.get("CARTYPE"));
                        if (sortKey(ct, carOrder) >= sortKey(vehCar, carOrder)) continue;
                        if (rollEffH(ct, pi.forkliftYn, vehInfo, cp) >= stackH) {
                            notes.add("[높이업그레이드] " + vehCar + "→" + ct);
                            vehCar = ct; upgraded = true; break;
                        }
                    }
                    if (!upgraded) {
                        notes.add(String.format("[높이초과-수동확인] %s 롤직경%.0fmm×%d단=%.2fm > 차량가용%.2fm",
                            sk, dmm, actualStack, stackH, effH));
                    }
                }
            }

            // ── 원지 3D 물리검증 (ROLL_3D_CHECK_YN=N 이면 스킵) ────────
            VehInfo vi3d  = vehInfo.getOrDefault(vehCar, VehInfo.EMPTY);
            RollPhysics3D rp3d;
            if (cp.roll3dCheck) {
                rp3d = verifyRolls3D(b.items, skumaMap, vi3d, cp);
            } else {
                rp3d = RollPhysics3D.fail("[3D-원지검증] 비활성화(ROLL_3D_CHECK_YN=N) — 스킵");
                rp3d.fits = true;  // 검증 스킵 시 업그레이드 로직 미동작하도록 통과 처리
            }
            notes.add(rp3d.summary);
            notes.addAll(rp3d.layerNotes);

            // 3D 검증 결과 적재 불가 → 더 큰 차량으로 업그레이드 시도
            if (cp.roll3dCheck && !rp3d.fits && !rp3d.summary.contains("스킵")) {
                boolean upgraded3d = false;
                for (Map<String, Object> c : carOrder) {
                    String ct = str(c.get("CARTYPE"));
                    if (sortKey(ct, carOrder) >= sortKey(vehCar, carOrder)) continue;
                    VehInfo cvi = vehInfo.getOrDefault(ct, VehInfo.EMPTY);
                    RollPhysics3D rp3dUp = verifyRolls3D(b.items, skumaMap, cvi, cp);
                    if (rp3dUp.fits) {
                        notes.add(String.format("[3D-원지-업그레이드] %s→%s (3D 공간 부족 해소)", vehCar, ct));
                        vehCar = ct; upgraded3d = true;
                        notes.add(rp3dUp.summary);
                        break;
                    }
                }
                if (!upgraded3d) notes.add("[3D-원지-수동확인] 가용 차량 중 3D 배치 가능한 차량 없음 — 수동 배차 필요");
            }
            // ─────────────────────────────────────────────────────────

            VehInfo vi    = vehInfo.getOrDefault(vehCar, VehInfo.EMPTY);
            double cap    = vi.loadKg;
            double fill   = cap > 0 ? vehKg / cap * 100 : 0;
            double costVal = routeCostMap.getOrDefault(dptnky, Collections.emptyMap())
                             .getOrDefault(vehCar, 0.0);
            int totalRc    = b.items.stream()
                .filter(it -> isRoll(str(it.get("SKUKEY"))))
                .mapToInt(it -> itemRollCount(it, skumaMap, cp.rollSingleKg)).sum();
            notes.add(String.format("[%s] %s 선정 (적재%.0fkg / 한도%.0fkg / 적재율%.1f%%%s)",
                objective, vehCar, vehKg, cap, fill,
                costVal > 0 ? String.format(" / 운송비%,.0f원", costVal) : ""));
            if (isMixedLoad) {
                notes.add("[혼적-Z축] 원지 하단(바닥) / 판지 상단 배치 강제 (파손 방지)");
                notes.add("[혼적-Y축] LIFO: 나중 하차→안쪽 / 먼저 하차→문 쪽 배치");
            }

            Map<String, Object> vrow = new LinkedHashMap<>();
            vrow.put("dptnky",        dptnky);   vrow.put("dptnm",    dptnm);
            vrow.put("rqshpd",        rqshpd);   vrow.put("cartype",  vehCar);
            vrow.put("total_kg",      round2(vehKg));
            vrow.put("load_cap",      cap);
            vrow.put("spare_kg",      round2(cap - vehKg));
            vrow.put("fill_ratio",    round2(fill));
            vrow.put("items",         b.items);
            vrow.put("item_cnt",      b.items.size());
            vrow.put("material_type", "ROLL");
            vrow.put("roll_count",    totalRc);
            vrow.put("route_cost",    costVal);
            vrow.put("objective",     objective);
            vrow.put("profile_id",    pid);
            vrow.put("profile_nm",    str(prof.get("PROFILE_NM")));
            vrow.put("notes",         notes);
            vrow.put("physics3d_roll_fits",        rp3d.fits);
            vrow.put("physics3d_roll_floor_pct",   round2(rp3d.usedFloorRatio));
            vrow.put("physics3d_roll_stack_height", round2(rp3d.stackHeightM));
            vrow.put("is_mixed",      isMixedGroup);
            vrow.put("is_mixed_load", isMixedLoad);
            vrow.put("mixed_dptnm",   isMixedGroup ? dptnm : "");
            vrow.put("forklift_yn",   pi.forkliftYn);
            vrow.put("dynamic_yn",    pi.dynamicYn);
            vrow.put("deadline_time", pi.deadlineTime);
            vrow.put("max_ton_label", pi.maxTonLabel);
            allVehicles.add(vrow);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  판지 배차 (CBM + 중량 이중 한계 검증 Double-Threshold Check)
    // ════════════════════════════════════════════════════════════════

    private void processBoardItems(
        List<Map<String, Object>> boardItems,
        String dptnky, String dptnm, String rqshpd,
        List<Map<String, Object>> validCars,
        Map<String, VehInfo> vehInfo,
        List<Map<String, Object>> carOrder,
        Map<String, SkuInfo> skumaMap,
        Map<String, Map<String, Double>> routeCostMap,
        Map<String, PtnrInfo> ptnrInfoMap,
        String objective, ConstraintParams cp,
        long pid, Map<String, Object> prof,
        boolean isMixedGroup, boolean isMixedLoad,
        boolean isDynBlocked, PtnrInfo pi,
        List<Map<String, Object>> allVehicles
    ) {
        String bigCar = validCars.isEmpty() ? "판별불가" : str(validCars.get(0).get("CARTYPE"));
        VehInfo bigVi = vehInfo.getOrDefault(bigCar, VehInfo.EMPTY);
        double bigCap = bigVi.loadKg > 0 ? bigVi.loadKg : 99_999_999.0;
        double bigH   = bigVi.effectiveHeightM;
        double bigCbm = bigVi.lengthM * bigVi.widthM * bigH;

        // Double-Threshold: 중량 OR CBM 초과 시 새 차량
        // 상한 비율 반영: BOARD_MAX_TON_RATIO(중량), BOARD_MAX_CBM_RATIO(CBM)
        // CBM 검증 토글: BOARD_CBM_CHECK_YN=N 이면 CBM 초과 판단 스킵(중량만 검사)
        double capKgThreshold  = bigCap * cp.boardMaxTonRatio;
        double capCbmThreshold = bigCbm * cp.boardMaxCbmRatio;
        List<BoardBin> vehListB = new ArrayList<>();
        List<Map<String, Object>> curB = new ArrayList<>();
        double curKg = 0, curH = 0, curCbm = 0;

        for (Map<String, Object> it : boardItems) {
            double qtyKg  = boardKg(it, skumaMap);
            double itemH  = Math.min(calcBoardHeight(it, skumaMap), cp.maxBoardHeightM);
            double itemCbm = getItemCbm(it, skumaMap);

            boolean ko = !curB.isEmpty() && (curKg + qtyKg > capKgThreshold);
            boolean co = cp.boardCbmCheck && !curB.isEmpty() && itemCbm > 0 && capCbmThreshold > 0
                         && (curCbm + itemCbm > capCbmThreshold);
            if (ko || co) {
                vehListB.add(new BoardBin(new ArrayList<>(curB), curKg, curH, curCbm, ko ? "중량초과" : "CBM초과"));
                curB.clear(); curKg = 0; curH = 0; curCbm = 0;
            }
            curB.add(it); curKg += qtyKg;
            curH = Math.max(curH, itemH);
            curCbm += itemCbm;
        }
        if (!curB.isEmpty()) vehListB.add(new BoardBin(new ArrayList<>(curB), curKg, curH, curCbm, ""));

        for (int vbIdx = 0; vbIdx < vehListB.size(); vbIdx++) {
            BoardBin vb = vehListB.get(vbIdx);
            double vehKg = vb.totalKg;
            // BOARD 재질: BOARD_MIN_FILL_RATIO 하한 적용 (Flask material_type='BOARD' 동일)
            String vehCar = selectCar(vehKg, validCars, vehInfo, dptnky, objective, cp, routeCostMap, true);
            VehInfo vi    = vehInfo.getOrDefault(vehCar, VehInfo.EMPTY);
            double cap    = vi.loadKg;
            double fill   = cap > 0 ? vehKg / cap * 100 : 0;
            double costVal = routeCostMap.getOrDefault(dptnky, Collections.emptyMap())
                              .getOrDefault(vehCar, 0.0);

            double vehEffH   = vi.effectiveHeightM;
            double vehCbmCap = vi.lengthM * vi.widthM * vehEffH;
            double cbmFill   = vehCbmCap > 0 ? vb.totalCbm / vehCbmCap * 100 : 0;

            List<String> notesB = new ArrayList<>();
            notesB.add(String.format("[%s] %s 선정 (적재%.0fkg / 한도%.0fkg / 적재율%.1f%%%s%s)",
                objective, vehCar, vehKg, cap, fill,
                vb.totalCbm > 0 ? String.format(" / CBM%.2fm³/%.1fm³(%.0f%%)", vb.totalCbm, vehCbmCap, cbmFill) : "",
                costVal > 0 ? String.format(" / 운송비%,.0f원", costVal) : ""));

            // 비정수 Ream 경고
            if (cp.boardBulkIntOnly) {
                for (Map<String, Object> it : vb.items) {
                    String bqw = boardQtyWarn(it, cp.boardInnerSplit);
                    if (bqw != null) notesB.add(bqw);
                }
            }
            if (vbIdx > 0 && !vb.splitReason.isEmpty())
                notesB.add("[분할선적-판지] " + vb.splitReason + "으로 인한 후속 차량 배차");
            if (isDynBlocked) notesB.add("[동적배차불가] DYNAMIC_YN=N → 고정노선 전용 오더");
            else if ("Y".equals(pi.dynamicYn)) notesB.add("[동적배차가능] DYNAMIC_YN=Y");
            if (isMixedLoad) {
                notesB.add("[혼적-Z축] 원지 하단(바닥) / 판지 상단 배치 강제");
                notesB.add("[혼적-Y축] LIFO: 나중 하차→안쪽 / 먼저 하차→문 쪽");
            }

            // ── 판지 3D 물리검증 (BOARD_3D_CHECK_YN=N 이면 스킵) ───────
            BoardPhysics3D bp3d;
            if (cp.board3dCheck) {
                bp3d = verifyBoards3D(vb.items, skumaMap, vi, cp);
            } else {
                bp3d = BoardPhysics3D.fail("[3D-판지검증] 비활성화(BOARD_3D_CHECK_YN=N) — 스킵");
                bp3d.fits = true;  // 검증 스킵 시 업그레이드 로직 미동작하도록 통과 처리
            }
            notesB.add(bp3d.summary);
            notesB.addAll(bp3d.shelfNotes);

            // 3D 검증 실패 → 더 큰 차량으로 업그레이드 시도
            if (cp.board3dCheck && !bp3d.fits && !bp3d.summary.contains("스킵")) {
                boolean upgraded3d = false;
                for (Map<String, Object> c : carOrder) {
                    String ct = str(c.get("CARTYPE"));
                    if (sortKey(ct, carOrder) >= sortKey(vehCar, carOrder)) continue;
                    VehInfo cvi = vehInfo.getOrDefault(ct, VehInfo.EMPTY);
                    BoardPhysics3D bp3dUp = verifyBoards3D(vb.items, skumaMap, cvi, cp);
                    if (bp3dUp.fits) {
                        notesB.add(String.format("[3D-판지-업그레이드] %s→%s (3D 공간 부족 해소)", vehCar, ct));
                        vehCar = ct; vi = cvi; upgraded3d = true;
                        // 업그레이드 후 적재율/CBM 재계산
                        cap        = vi.loadKg;
                        fill       = cap > 0 ? vehKg / cap * 100 : 0;
                        vehEffH    = vi.effectiveHeightM;
                        vehCbmCap  = vi.lengthM * vi.widthM * vehEffH;
                        cbmFill    = vehCbmCap > 0 ? vb.totalCbm / vehCbmCap * 100 : 0;
                        costVal    = routeCostMap.getOrDefault(dptnky, Collections.emptyMap())
                                                  .getOrDefault(ct, 0.0);
                        notesB.add(bp3dUp.summary);
                        bp3d = bp3dUp;
                        break;
                    }
                }
                if (!upgraded3d) notesB.add("[3D-판지-수동확인] 가용 차량 중 3D 배치 가능한 차량 없음 — 수동 배차 필요");
            }
            // ─────────────────────────────────────────────────────────

            Map<String, Object> vrow = new LinkedHashMap<>();
            vrow.put("dptnky", dptnky); vrow.put("dptnm", dptnm); vrow.put("rqshpd", rqshpd);
            vrow.put("cartype", vehCar);
            vrow.put("total_kg",   round2(vehKg));
            vrow.put("load_cap",   cap);
            vrow.put("spare_kg",   round2(cap - vehKg));
            vrow.put("fill_ratio", round2(fill));
            vrow.put("items",      vb.items);
            vrow.put("item_cnt",   vb.items.size());
            vrow.put("material_type", "BOARD");
            vrow.put("roll_count",    0);
            vrow.put("total_cbm",     round4(vb.totalCbm));
            vrow.put("cbm_cap",       round2(vehCbmCap));
            vrow.put("cbm_fill",      round2(cbmFill));
            vrow.put("route_cost",    costVal);
            vrow.put("objective",     objective);
            vrow.put("profile_id",    pid);
            vrow.put("profile_nm",    str(prof.get("PROFILE_NM")));
            vrow.put("notes",         notesB);
            vrow.put("physics3d_board_fits",        bp3d.fits);
            vrow.put("physics3d_board_floor_pct",   round2(bp3d.usedFloorRatio));
            vrow.put("physics3d_board_max_height",  round2(bp3d.maxHeightM));
            vrow.put("physics3d_board_vol_m3",      round4(bp3d.usedVolM3));
            vrow.put("is_mixed",      isMixedGroup);
            vrow.put("is_mixed_load", isMixedLoad);
            vrow.put("mixed_dptnm",   isMixedGroup ? dptnm : "");
            vrow.put("forklift_yn",   pi.forkliftYn);
            vrow.put("dynamic_yn",    pi.dynamicYn);
            vrow.put("deadline_time", pi.deadlineTime);
            vrow.put("max_ton_label", pi.maxTonLabel);
            allVehicles.add(vrow);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  기타 품목 배차
    // ════════════════════════════════════════════════════════════════

    private void processOtherItems(
        List<Map<String, Object>> otherItems,
        String dptnky, String dptnm, String rqshpd,
        List<Map<String, Object>> validCars,
        Map<String, VehInfo> vehInfo,
        Map<String, Map<String, Double>> routeCostMap,
        String objective, ConstraintParams cp,
        long pid, Map<String, Object> prof,
        boolean isDynBlocked, PtnrInfo pi,
        List<Map<String, Object>> allVehicles
    ) {
        double totalKg = otherItems.stream().mapToDouble(it -> {
            double gw = dbl(it.get("KG_WEIGHT"));
            if (gw <= 0) gw = dbl(it.get("GRSWGT"));
            if (gw <= 0) gw = dbl(it.get("QTSHPO"));
            return gw;
        }).sum();

        String vehCar  = selectCar(totalKg, validCars, vehInfo, dptnky, objective, cp, routeCostMap);
        double cap     = vehInfo.getOrDefault(vehCar, VehInfo.EMPTY).loadKg;
        double fill    = cap > 0 ? totalKg / cap * 100 : 0;
        double costVal = routeCostMap.getOrDefault(dptnky, Collections.emptyMap())
                          .getOrDefault(vehCar, 0.0);

        List<String> notesO = new ArrayList<>();
        notesO.add(String.format("[%s] %s 선정 (기타품목 %d건 / 적재%.0fkg / 한도%.0fkg / 적재율%.1f%%%s)",
            objective, vehCar, otherItems.size(), totalKg, cap, fill,
            costVal > 0 ? String.format(" / 운송비%,.0f원", costVal) : ""));
        if (isDynBlocked) notesO.add("[동적배차불가] DYNAMIC_YN=N → 고정노선 전용 오더");

        Map<String, Object> vrow = new LinkedHashMap<>();
        vrow.put("dptnky", dptnky); vrow.put("dptnm", dptnm); vrow.put("rqshpd", rqshpd);
        vrow.put("cartype", vehCar);
        vrow.put("total_kg",      round2(totalKg));
        vrow.put("load_cap",      cap);
        vrow.put("spare_kg",      round2(cap - totalKg));
        vrow.put("fill_ratio",    round2(fill));
        vrow.put("items",         otherItems);
        vrow.put("item_cnt",      otherItems.size());
        vrow.put("material_type", "OTHER");
        vrow.put("roll_count",    0);
        vrow.put("total_cbm",     0.0);
        vrow.put("cbm_cap",       0.0);
        vrow.put("cbm_fill",      0.0);
        vrow.put("route_cost",    costVal);
        vrow.put("objective",     objective);
        vrow.put("profile_id",    pid);
        vrow.put("profile_nm",    str(prof.get("PROFILE_NM")));
        vrow.put("notes",         notesO);
        vrow.put("is_mixed",      false);
        vrow.put("is_mixed_load", false);
        vrow.put("mixed_dptnm",   "");
        vrow.put("forklift_yn",   pi.forkliftYn);
        vrow.put("dynamic_yn",    pi.dynamicYn);
        vrow.put("deadline_time", pi.deadlineTime);
        vrow.put("max_ton_label", pi.maxTonLabel);
        allVehicles.add(vrow);
    }

    // ════════════════════════════════════════════════════════════════
    //  목적식별 차량 선정 함수
    // ════════════════════════════════════════════════════════════════

    private String selectCar(double needKg,
                              List<Map<String, Object>> validCars,
                              Map<String, VehInfo> vehInfo,
                              String dptnky, String objective,
                              ConstraintParams cp,
                              Map<String, Map<String, Double>> routeCostMap) {
        return selectCar(needKg, validCars, vehInfo, dptnky, objective, cp, routeCostMap, false);
    }

    /** materialType='BOARD' 이면 BOARD_MIN_FILL_RATIO 하한이 적용됩니다 (Flask _select_car 완전 호환) */
    private String selectCar(double needKg,
                              List<Map<String, Object>> validCars,
                              Map<String, VehInfo> vehInfo,
                              String dptnky, String objective,
                              ConstraintParams cp,
                              Map<String, Map<String, Double>> routeCostMap,
                              boolean isBoard) {
        if ("MAX_FILL".equals(objective))  return selectCarMaxFill(needKg, validCars, vehInfo, cp);
        if ("MIN_COST".equals(objective))  return selectCarMinCost(needKg, validCars, vehInfo, dptnky, cp, routeCostMap, isBoard);
        return selectCarMinVehicles(needKg, validCars, vehInfo, cp, isBoard);
    }

    /** MIN_VEHICLES: 적재율 최대 (First-Fit Decreasing 기반) */
    private String selectCarMinVehicles(double needKg,
                                         List<Map<String, Object>> validCars,
                                         Map<String, VehInfo> vehInfo,
                                         ConstraintParams cp, boolean isBoard) {
        double minFill = isBoard ? cp.boardMinFill : cp.minFill;
        String best = null; double bestRatio = -1;
        String fallback = null; double fallbackRatio = -1;
        for (Map<String, Object> c : validCars) {
            String ct = str(c.get("CARTYPE"));
            double cap = vehInfo.getOrDefault(ct, VehInfo.EMPTY).loadKg;
            if (cap <= 0) continue;
            double r = needKg / cap;
            if (cap >= needKg) {
                if (minFill > 0 && r < minFill) {
                    if (r > fallbackRatio) { fallbackRatio = r; fallback = ct; }
                    continue;
                }
                if (r > bestRatio) { bestRatio = r; best = ct; }
            }
        }
        if (best != null) return best;
        if (fallback != null) return fallback;
        return validCars.isEmpty() ? "판별불가" : str(validCars.get(0).get("CARTYPE"));
    }

    /** MAX_FILL: 적재율 최대화 (Best-Fit, MIN_FILL 하한 준수) */
    private String selectCarMaxFill(double needKg,
                                    List<Map<String, Object>> validCars,
                                    Map<String, VehInfo> vehInfo,
                                    ConstraintParams cp) {
        String best = null; double bestRatio = -1;
        for (Map<String, Object> c : validCars) {
            String ct = str(c.get("CARTYPE"));
            double cap = vehInfo.getOrDefault(ct, VehInfo.EMPTY).loadKg;
            if (cap <= 0) continue;
            double ratio = needKg / cap;
            if (ratio > cp.maxFill) continue;  // 초과 금지
            if (ratio < cp.minFill) continue;  // 하한 미달 금지
            if (cap >= needKg && ratio > bestRatio) { bestRatio = ratio; best = ct; }
        }
        if (best != null) return best;
        return selectCarMinVehicles(needKg, validCars, vehInfo, cp, false);
    }

    /** MIN_COST: ROUTE_COST 기반 최저비용 */
    private String selectCarMinCost(double needKg,
                                     List<Map<String, Object>> validCars,
                                     Map<String, VehInfo> vehInfo,
                                     String dptnky, ConstraintParams cp,
                                     Map<String, Map<String, Double>> routeCostMap,
                                     boolean isBoard) {
        Map<String, Double> costs = routeCostMap.getOrDefault(dptnky, Collections.emptyMap());
        String bestCar = null; double bestCost = Double.MAX_VALUE;
        for (Map<String, Object> c : validCars) {
            String ct  = str(c.get("CARTYPE"));
            double cap = vehInfo.getOrDefault(ct, VehInfo.EMPTY).loadKg;
            if (cap <= 0) continue;
            double baseCost = costs.getOrDefault(ct, 0.0);
            if (baseCost <= 0) baseCost = cap / 1000.0 * 100_000.0;  // fallback 추정
            double actualCost;
            if (cap < needKg) {
                actualCost = baseCost * cp.penalty;
            } else {
                double wasteRatio = (cap - needKg) / cap;
                actualCost = baseCost * (1.0 + wasteRatio * 0.1);
            }
            if (actualCost < bestCost) { bestCost = actualCost; bestCar = ct; }
        }
        if (bestCar != null) return bestCar;
        return selectCarMinVehicles(needKg, validCars, vehInfo, cp, isBoard);
    }

    // ════════════════════════════════════════════════════════════════
    //  DB 조회 헬퍼
    // ════════════════════════════════════════════════════════════════

    /** DS_DISPATCH_PROFILE 로드 — Oracle: FETCH FIRST N ROWS ONLY */
    private Map<String, Object> loadProfile(Integer profileId) {
        List<Map<String, Object>> rows;
        if (profileId != null) {
            rows = tmsJdbc.queryForList("SELECT * FROM KNRAWMS.DS_DISPATCH_PROFILE WHERE PROFILE_ID=?", profileId);
        } else {
            rows = tmsJdbc.queryForList(
                "SELECT * FROM KNRAWMS.DS_DISPATCH_PROFILE WHERE ACTIVE_YN='Y' ORDER BY PROFILE_ID FETCH FIRST 1 ROWS ONLY");
        }
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 출고 아이템 조회 — Oracle KNRAWMS: SHPDH, SHPDI, BZPTN */
    private List<Map<String, Object>> fetchItemsByDate(String yyyymmdd, String ptnrkyFilter) {
        StringBuilder sql = new StringBuilder(
            "SELECT h.SHPOKY, h.DPTNKY, b.NAME01 AS DPTNM, h.RQSHPD, " +
            "       i.SHPOIT, i.SKUKEY, i.QTSHPO, i.DESC01 AS SKUNM, " +
            "       i.GRSWGT, i.WGTUNT, i.UOMKEY, i.LENGTH, i.WIDTHW AS WIDTH_MM, i.HEIGHT " +
            "FROM KNRAWMS.SHPDH h JOIN KNRAWMS.SHPDI i ON h.SHPOKY=i.SHPOKY " +
            "LEFT JOIN KNRAWMS.BZPTN b ON h.DPTNKY=b.PTNRKY AND b.PTNRTY='CT' " +
            "WHERE h.RQSHPD=?"
        );
        List<Object> args = new ArrayList<>();
        args.add(yyyymmdd);
        if (!ptnrkyFilter.isEmpty()) { sql.append(" AND h.DPTNKY=?"); args.add(ptnrkyFilter); }
        return wmsJdbc.queryForList(sql.toString(), args.toArray());
    }

    /**
     * 차량 순서 로드 — DS_VEHICLE: Oracle KNRAWMS / CMCDV: Oracle KNRAWMS (2-step, Cross-Schema)
     * Step 1: DS_VEHICLE, Step 2: KNRAWMS.CMCDV (2-step)
     */
    private List<Map<String, Object>> loadCarOrder() {
        // Step 1: Oracle KNRAWMS DS_VEHICLE
        List<Map<String, Object>> all = tmsJdbc.queryForList(
            "SELECT v.CARTYPE, v.LOAD_TON, v.SORT_SEQ, v.CARCLASS_CD FROM KNRAWMS.DS_VEHICLE v ORDER BY v.SORT_SEQ DESC"
        );
        // Step 2: Oracle KNRAWMS.CMCDV — USE_YN 필터
        List<Map<String, Object>> ccRows = wmsJdbc.queryForList(
            "SELECT CMCDVL, USARG1 FROM KNRAWMS.CMCDV WHERE CMCDKY='TMS_CARCLASS10'"
        );
        Map<String, String> useYnMap = new HashMap<>();
        for (Map<String, Object> r : ccRows) useYnMap.put(str(r.get("CMCDVL")), str(r.get("USARG1")));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> v : all) {
            String cc = str(v.get("CARCLASS_CD"));
            String useYn = useYnMap.getOrDefault(cc, "Y");
            if ("Y".equalsIgnoreCase(useYn) || useYn.isEmpty()) result.add(v);
        }
        return result;
    }

    /** 차량 상세 정보 로드 — DS_VEHICLE: Oracle KNRAWMS / CMCDV USE_YN 필터: Oracle KNRAWMS → 2-step */
    private Map<String, VehInfo> loadVehInfo() {
        List<Map<String, Object>> rows = tmsJdbc.queryForList(
            "SELECT v.CARTYPE, v.LENGTH_M, v.WIDTH_M, v.HEIGHT_M, v.LOAD_TON, " +
            "       v.PALLET_HEIGHT_M, v.CARCLASS_CD FROM KNRAWMS.DS_VEHICLE v"
        );
        // CMCDV USE_YN 필터 (Oracle)
        List<Map<String, Object>> ccRows = wmsJdbc.queryForList(
            "SELECT CMCDVL, USARG1 FROM KNRAWMS.CMCDV WHERE CMCDKY='TMS_CARCLASS10'"
        );
        Map<String, String> useYnMap = new HashMap<>();
        for (Map<String, Object> r : ccRows) useYnMap.put(str(r.get("CMCDVL")), str(r.get("USARG1")));
        Map<String, VehInfo> result = new HashMap<>();
        for (Map<String, Object> r : rows) {
            String cc = str(r.get("CARCLASS_CD"));
            String useYn = useYnMap.getOrDefault(cc, "Y");
            if (!"Y".equalsIgnoreCase(useYn) && !useYn.isEmpty()) continue;
            VehInfo vi = new VehInfo();
            vi.heightM          = dbl(r.get("HEIGHT_M"));
            vi.palletHeightM    = dbl(r.get("PALLET_HEIGHT_M"));
            vi.effectiveHeightM = Math.max(0, vi.heightM - vi.palletHeightM);
            vi.widthM           = parseVehicleWidth(str(r.get("WIDTH_M")));
            vi.lengthM          = dbl(r.get("LENGTH_M"));
            vi.loadKg           = dbl(r.get("LOAD_TON")) * 1000.0;
            vi.classCode        = str(r.get("CARCLASS_CD"));
            result.put(str(r.get("CARTYPE")), vi);
        }
        return result;
    }

    /** DS_INCH12 / DS_INCH3 로드 — Oracle KNRAWMS */
    private InchMaps loadInchMaps() {
        InchMaps m = new InchMaps();
        List<Map<String, Object>> i12 = tmsJdbc.queryForList("SELECT CARTYPE,GRM_COND,MAX_COUNT FROM KNRAWMS.DS_INCH12");
        List<Map<String, Object>> i3  = tmsJdbc.queryForList("SELECT CARTYPE,GRM_COND,MAX_COUNT FROM KNRAWMS.DS_INCH3");
        for (Map<String, Object> r : i12)
            m.inch12.computeIfAbsent(str(r.get("CARTYPE")), k -> new HashMap<>())
                    .put(str(r.get("GRM_COND")), (int) dbl(r.get("MAX_COUNT")));
        for (Map<String, Object> r : i3)
            m.inch3.computeIfAbsent(str(r.get("CARTYPE")), k -> new HashMap<>())
                   .put(str(r.get("GRM_COND")), (int) dbl(r.get("MAX_COUNT")));
        return m;
    }

    /** SKUMA 로드 — Oracle KNRAWMS */
    private Map<String, SkuInfo> loadSkumaMap() {
        List<Map<String, Object>> rows = wmsJdbc.queryForList(
            "SELECT SKUKEY, GRSWGT, ASKL04, ASKL05, CUBICM FROM KNRAWMS.SKUMA WHERE MTYPE='P'"
        );
        Map<String, SkuInfo> result = new HashMap<>();
        for (Map<String, Object> r : rows) {
            SkuInfo si = new SkuInfo();
            si.grswgt  = dbl(r.get("GRSWGT"));
            si.wMm     = parseIntSafe(str(r.get("ASKL04")));
            si.lMm     = parseIntSafe(str(r.get("ASKL05")));
            si.cubicm  = dbl(r.get("CUBICM"));
            result.put(str(r.get("SKUKEY")), si);
        }
        return result;
    }

    /**
     * 운송비 로드 — ROUTE_COST: Oracle KNRAWMS / CMCDV: Oracle KNRAWMS → 2-step
     * Cross-DB 조인 불가 → CARCLASS 코드명 별도 조회 후 매핑
     */
    private Map<String, Map<String, Double>> loadRouteCostMap(String costDate) {
        // Step 1: Oracle KNRAWMS ROUTE_COST
        List<Map<String, Object>> rows = tmsJdbc.queryForList(
            "SELECT rc.PTNRKY, rc.CARCLASS, rc.COST " +
            "FROM KNRAWMS.ROUTE_COST rc " +
            "WHERE rc.DATE_START<=? AND rc.DATE_END>=?", costDate, costDate
        );
        // Step 2: Oracle KNRAWMS.CMCDV — CARCLASS 코드 → 차종명 매핑
        List<Map<String, Object>> ccRows = wmsJdbc.queryForList(
            "SELECT CMCDVL, CDESC1 FROM KNRAWMS.CMCDV WHERE CMCDKY='TMS_CARCLASS10'"
        );
        Map<String, String> ccMap = new HashMap<>();
        for (Map<String, Object> r : ccRows) ccMap.put(str(r.get("CMCDVL")), str(r.get("CDESC1")));

        Map<String, Map<String, Double>> result = new HashMap<>();
        for (Map<String, Object> r : rows) {
            String ct = ccMap.get(str(r.get("CARCLASS")));
            if (ct != null && !ct.isEmpty()) {
                result.computeIfAbsent(str(r.get("PTNRKY")), k -> new HashMap<>())
                      .put(ct, dbl(r.get("COST")));
            }
        }
        return result;
    }

    /** 납품처 TMS 정보 로드 — BZPTN_DETAIL, CMCDV: Oracle KNRAWMS */
    private Map<String, PtnrInfo> loadPtnrInfo(List<String> dptnkyList,
                                                 Map<String, VehInfo> vehInfo) {
        if (dptnkyList.isEmpty()) return Collections.emptyMap();
        String ph = dptnkyList.stream().map(x -> "?").collect(Collectors.joining(","));
        List<Map<String, Object>> rows = wmsJdbc.queryForList(
            "SELECT PTNRKY,DEADLINE_TIME,FORKLIFT_YN,MAX_TON,DYNAMIC_YN " +
            "FROM KNRAWMS.BZPTN_DETAIL WHERE PTNRKY IN (" + ph + ") AND PTNRTY='CT'",
            dptnkyList.toArray()
        );
        // CARCLASS10 코드명 매핑 (Oracle)
        List<Map<String, Object>> ccRows = wmsJdbc.queryForList(
            "SELECT CMCDVL,CDESC1 FROM KNRAWMS.CMCDV WHERE CMCDKY='TMS_CARCLASS10'"
        );
        Map<String, String> ccMap = new HashMap<>();
        for (Map<String, Object> r : ccRows) ccMap.put(str(r.get("CMCDVL")), str(r.get("CDESC1")));

        Map<String, PtnrInfo> result = new HashMap<>();
        for (Map<String, Object> r : rows) {
            PtnrInfo pi = new PtnrInfo();
            String mt   = str(r.get("MAX_TON"));
            pi.deadlineTime = str(r.get("DEADLINE_TIME"));
            pi.forkliftYn   = str(r.get("FORKLIFT_YN"));
            pi.dynamicYn    = str(r.get("DYNAMIC_YN")).toUpperCase();
            pi.maxTonLabel  = ccMap.getOrDefault(mt, mt);
            pi.maxLoadKg    = pi.maxTonLabel.isEmpty() ? 0
                : vehInfo.getOrDefault(pi.maxTonLabel, VehInfo.EMPTY).loadKg;
            result.put(str(r.get("PTNRKY")), pi);
        }
        return result;
    }

    // ════════════════════════════════════════════════════════════════
    //  그룹핑
    // ════════════════════════════════════════════════════════════════

    private Map<String, List<Map<String, Object>>> buildGroups(
            List<Map<String, Object>> items, boolean allowMixedLoad) {
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();

        if (allowMixedLoad) {
            // 우편번호 앞 3자리 기반 혼적 그룹
            List<String> allDks = items.stream()
                .map(it -> str(it.get("DPTNKY"))).distinct().collect(Collectors.toList());
            Map<String, String> postcdMap = new HashMap<>();
            if (!allDks.isEmpty()) {
                String ph = allDks.stream().map(x -> "?").collect(Collectors.joining(","));
                List<Map<String, Object>> prows = wmsJdbc.queryForList(
                    "SELECT PTNRKY, POSTCD FROM KNRAWMS.BZPTN WHERE PTNRKY IN (" + ph + ") AND PTNRTY='CT'",
                    allDks.toArray());
                for (Map<String, Object> r : prows) {
                    String pc = str(r.get("POSTCD"));
                    postcdMap.put(str(r.get("PTNRKY")), pc.length() >= 3 ? pc.substring(0,3) : pc);
                }
            }
            for (Map<String, Object> it : items) {
                String dk = str(it.get("DPTNKY")); String rq = str(it.get("RQSHPD"));
                String zip3 = postcdMap.getOrDefault(dk, "");
                String key  = !zip3.isEmpty() ? "_ZIP_" + zip3 + "|" + zip3 + "|" + rq
                                              : dk + "|" + str(it.get("DPTNM")) + "|" + rq;
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(it);
            }
        } else {
            for (Map<String, Object> it : items) {
                String key = str(it.get("DPTNKY")) + "|" + str(it.get("DPTNM")) + "|" + str(it.get("RQSHPD"));
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(it);
            }
        }
        return groups;
    }

    // ════════════════════════════════════════════════════════════════
    //  유효 차량 목록 (허용 차종 + MAX_TON 필터)
    // ════════════════════════════════════════════════════════════════

    private List<Map<String, Object>> getValidCars(
            String dptnky,
            List<Map<String, Object>> carOrder,
            Map<String, VehInfo> vehInfo,
            Set<String> allowedCartypes,
            Map<String, PtnrInfo> ptnrInfoMap,
            Map<String, Map<String, Map<String, Object>>> Cbt,
            ConstraintParams cp) {
        List<Map<String, Object>> cars = carOrder.stream()
            .filter(c -> vehInfo.getOrDefault(str(c.get("CARTYPE")), VehInfo.EMPTY).loadKg > 0)
            .collect(Collectors.toList());

        if (!allowedCartypes.isEmpty()) {
            cars = cars.stream()
                .filter(c -> allowedCartypes.contains(str(c.get("CARTYPE"))))
                .collect(Collectors.toList());
        }

        // ── 진입 톤수 제한 (ENTRY_TON_LIMIT): 설정값(ton) 초과 차량 후보 제외 ──
        // 납품처 마스터(MAX_TON)와 별개로, 제약조건관리 화면의 전역 진입 제한을 적용
        if (cp != null && cp.entryTonLimit > 0) {
            double limitKg = cp.entryTonLimit * 1000.0;
            List<Map<String, Object>> filtered = cars.stream()
                .filter(c -> vehInfo.getOrDefault(str(c.get("CARTYPE")), VehInfo.EMPTY).loadKg <= limitKg)
                .collect(Collectors.toList());
            if (!filtered.isEmpty()) cars = filtered;
        }

        if (!dptnky.isEmpty()) {
            PtnrInfo pi = ptnrInfoMap.getOrDefault(dptnky, PtnrInfo.EMPTY);
            if (pi.maxLoadKg > 0) {
                List<Map<String, Object>> filtered = cars.stream()
                    .filter(c -> vehInfo.getOrDefault(str(c.get("CARTYPE")), VehInfo.EMPTY).loadKg <= pi.maxLoadKg)
                    .collect(Collectors.toList());
                if (!filtered.isEmpty()) cars = filtered;
            }
            // FORCE_CARTYPE 납품처별 제약 (고정차량 지정)
            // FIXED_VEH_PRIORITY=Y 일 때만 고정차량을 최우선(사전할당)으로 강제 적용
            Map<String, Object> forceCr = Cbt.getOrDefault(dptnky, Collections.emptyMap())
                                            .get("FORCE_CARTYPE");
            if (forceCr != null && (cp == null || cp.fixedVehPriority)) {
                String forceCt = str(forceCr.get("CONST_VALUE"));
                List<Map<String, Object>> forced = cars.stream()
                    .filter(c -> forceCt.equals(str(c.get("CARTYPE"))))
                    .collect(Collectors.toList());
                if (!forced.isEmpty()) cars = forced;
            }
        }
        return cars;
    }

    // ════════════════════════════════════════════════════════════════
    //  SKUKEY 파싱 헬퍼
    // ════════════════════════════════════════════════════════════════

    private String getInch(String sk) {
        if (sk == null || sk.length() < 5) return "";
        String m = sk.substring(2, 5).toLowerCase();
        if (INCH12.contains(m)) return "12인치";
        if (INCH3.contains(m))  return "3인치";
        return "";
    }

    private String getGrm(String sk) {
        try {
            int g = Integer.parseInt(sk.substring(5, 8));
            return g >= 300 ? "GE300" : "LT300";
        } catch (Exception e) { return "LT300"; }
    }

    private boolean isRoll(String sk) {
        if (sk == null || sk.length() < 17) return false;
        if (!"-".equals(sk.substring(8, 9))) return false;
        return "0000".equals(sk.substring(13, 17));
    }

    private boolean isBoard(String sk) {
        if (sk == null || sk.length() < 17) return false;
        if (!"-".equals(sk.substring(8, 9))) return false;
        String lp = sk.substring(13, 17);
        return !lp.equals("0000") && lp.matches("\\d+");
    }

    private boolean isRollItem(Map<String, Object> it) {
        String sk  = str(it.get("SKUKEY"));
        String uom = str(it.get("UOMKEY"));
        if (isRoll(sk)) return true;
        return "R".equals(uom) && sk.length() > 0 && sk.charAt(0) == 'H';
    }

    private boolean isBoardItem(Map<String, Object> it) {
        if (isRollItem(it)) return false;
        return isBoard(str(it.get("SKUKEY")));
    }

    private int[] parseSkyueyDims(String sk) {
        try {
            int gsm = Integer.parseInt(sk.substring(5, 8));
            int wmm = Integer.parseInt(sk.substring(9, 13));
            return new int[]{gsm, wmm};
        } catch (Exception e) { return null; }
    }

    private int[] parseBoardDims(String sk) {
        if (sk == null || sk.length() < 17 || !"-".equals(sk.substring(8, 9))) return null;
        try {
            int w = Integer.parseInt(sk.substring(9, 13));
            int l = Integer.parseInt(sk.substring(13, 17));
            return new int[]{w, l};
        } catch (Exception e) { return null; }
    }

    // ════════════════════════════════════════════════════════════════
    //  물리량 계산 헬퍼
    // ════════════════════════════════════════════════════════════════

    private Double calcRollDiameter(double weightKg, String sk) {
        int[] dims = parseSkyueyDims(sk);
        if (dims == null) return null;
        int gsm = dims[0], wmm = dims[1];
        if (gsm <= 0 || wmm <= 0) return null;
        try {
            double widthM = wmm / 1000.0;
            double tM     = (gsm / 1000.0) / PAPER_DENSITY;
            double weightG = weightKg * 1000.0;
            double lM     = weightG / (gsm * widthM);
            double dSq    = 4.0 * lM * tM / Math.PI;
            return dSq > 0 ? Math.sqrt(dSq) * 1000.0 : null;
        } catch (Exception e) { return null; }
    }

    private double calcBoardHeight(Map<String, Object> it, Map<String, SkuInfo> skumaMap) {
        String sk  = str(it.get("SKUKEY"));
        SkuInfo sm = skumaMap.getOrDefault(sk, new SkuInfo());
        double grswgt = sm.grswgt, wMm = sm.wMm, lMm = sm.lMm;
        if (grswgt <= 0 || wMm <= 0 || lMm <= 0) {
            int[] bd = parseBoardDims(sk);
            if (bd != null) { wMm = bd[0]; lMm = bd[1]; }
        }
        if (grswgt <= 0 || wMm <= 0 || lMm <= 0) return 0.0;
        int[] dims = parseSkyueyDims(sk);
        if (dims == null || dims[0] <= 0) return 0.0;
        int gsm = dims[0];
        double tMm = (gsm / 1_000_000.0) / PAPER_DENSITY_G_PER_MM3;
        double grswgtG = grswgt * 1000.0;
        double areaMm2 = wMm * lMm;
        double gsmPerMm2 = gsm / 1_000_000.0;
        if (gsmPerMm2 * areaMm2 <= 0) return 0.0;
        double sheets = grswgtG / (gsmPerMm2 * areaMm2);
        return sheets * tMm / 1000.0;
    }

    private double getItemCbm(Map<String, Object> it, Map<String, SkuInfo> skumaMap) {
        String sk  = str(it.get("SKUKEY"));
        SkuInfo sm = skumaMap.getOrDefault(sk, new SkuInfo());
        double grswgt = sm.grswgt, wMm = sm.wMm, lMm = sm.lMm;
        if (wMm <= 0 || lMm <= 0) {
            int[] bd = parseBoardDims(sk);
            if (bd != null) { wMm = bd[0]; lMm = bd[1]; }
        }
        double qtyKg;
        if (dbl(it.get("KG_WEIGHT")) > 0) qtyKg = dbl(it.get("KG_WEIGHT"));
        else {
            String uom = str(it.get("UOMKEY")); double qtshpo = dbl(it.get("QTSHPO"));
            qtyKg = ("R".equals(uom) && grswgt > 0) ? qtshpo * grswgt : qtshpo;
        }
        if (qtyKg <= 0) return 0.0;
        double bundles = grswgt > 0 ? qtyKg / grswgt : 1.0;
        if (sm.cubicm > 0) return sm.cubicm * bundles;
        if (wMm > 0 && lMm > 0 && grswgt > 0) {
            int[] dims = parseSkyueyDims(sk);
            if (dims != null && dims[0] > 0) {
                int gsm = dims[0];
                double tMm = (gsm / 1_000_000.0) / PAPER_DENSITY_G_PER_MM3;
                double gsmPerMm2 = gsm / 1_000_000.0, areaMm2 = wMm * lMm;
                if (gsmPerMm2 * areaMm2 > 0) {
                    double grswgtG = grswgt * 1000.0;
                    double sheets  = grswgtG / (gsmPerMm2 * areaMm2);
                    double bundleH = sheets * tMm;
                    return (wMm / 1000.0) * (lMm / 1000.0) * (bundleH / 1000.0) * bundles;
                }
            }
        }
        return 0.0;
    }

    private double itemRollKg(Map<String, Object> it, Map<String, SkuInfo> skumaMap, double rollSingleKg) {
        double kw = dbl(it.get("KG_WEIGHT"));
        if (kw > 0) return kw;
        String sk  = str(it.get("SKUKEY"));
        String uom = str(it.get("UOMKEY"));
        double qty = dbl(it.get("QTSHPO"));
        if ("R".equals(uom) && isRoll(sk)) {
            double gw = skumaMap.getOrDefault(sk, new SkuInfo()).grswgt;
            if (gw > 0) return qty * gw;
            double itemGw = dbl(it.get("GRSWGT"));
            if (itemGw > 0) return qty * itemGw;
            return qty * rollSingleKg;
        }
        return qty;
    }

    private int itemRollCount(Map<String, Object> it, Map<String, SkuInfo> skumaMap, double rollSingleKg) {
        String sk  = str(it.get("SKUKEY"));
        String uom = str(it.get("UOMKEY"));
        if ("R".equals(uom) && isRoll(sk)) return (int) dbl(it.get("QTSHPO"));
        double unitW = dbl(it.get("UNIT_WEIGHT"));
        double single = unitW > 0 ? unitW : rollSingleKg;
        double kg = itemRollKg(it, skumaMap, rollSingleKg);
        return kg > 0 && single > 0 ? (int) Math.ceil(kg / single) : 0;
    }

    private double boardKg(Map<String, Object> it, Map<String, SkuInfo> skumaMap) {
        double kw = dbl(it.get("KG_WEIGHT"));
        if (kw > 0) return kw;
        String uom = str(it.get("UOMKEY"));
        double qty = dbl(it.get("QTSHPO"));
        if ("R".equals(uom)) {
            String sk = str(it.get("SKUKEY"));
            double gw = skumaMap.getOrDefault(sk, new SkuInfo()).grswgt;
            if (gw > 0) return qty * gw;
            double itemGw = dbl(it.get("GRSWGT"));
            if (itemGw > 0) return qty * itemGw;
        }
        return qty;
    }

    private boolean canFitRollBin(RollBin b, double itemKg, String inch, String grm,
                                   int rc, double bigCap,
                                   Map<String, Integer> bI12, Map<String, Integer> bI3) {
        if (b.totalKg + itemKg > bigCap) return false;
        if ("12인치".equals(inch)) {
            int mc = bI12.getOrDefault(grm, 0);
            if (mc > 0 && b.v12.getOrDefault(grm, 0) + rc > mc) return false;
        } else if ("3인치".equals(inch)) {
            int mc = bI3.getOrDefault(grm, 0);
            if (mc > 0 && b.v3.getOrDefault(grm, 0) + rc > mc) return false;
        }
        return true;
    }

    private String findCar(Map<String, Map<String, Integer>> inchMap,
                            String grm, int count,
                            List<Map<String, Object>> carOrder,
                            Map<String, VehInfo> vehInfo) {
        List<Map<String, Object>> reversed = new ArrayList<>(carOrder);
        Collections.reverse(reversed);
        for (Map<String, Object> car : reversed) {
            String ct  = str(car.get("CARTYPE"));
            int cap = inchMap.getOrDefault(ct, Collections.emptyMap()).getOrDefault(grm, 0);
            if (cap >= count) return ct;
        }
        // 초과 → LOAD_TON 입력된 가장 큰 차량
        for (Map<String, Object> car : carOrder) {
            String ct = str(car.get("CARTYPE"));
            if (vehInfo.getOrDefault(ct, VehInfo.EMPTY).loadKg > 0) return ct;
        }
        return carOrder.isEmpty() ? "판별불가" : str(carOrder.get(0).get("CARTYPE"));
    }

    private int sortKey(String ct, List<Map<String, Object>> carOrder) {
        for (int i = 0; i < carOrder.size(); i++) {
            if (ct.equals(str(carOrder.get(i).get("CARTYPE")))) return i;
        }
        return 999;
    }

    private double rollEffH(String cartype, String forkliftYn, Map<String, VehInfo> vehInfo, ConstraintParams cp) {
        VehInfo vi = vehInfo.getOrDefault(cartype, VehInfo.EMPTY);
        // 포크리프트 보유 납품처: 전체 높이 사용 (파레트 불필요)
        if ("Y".equals(forkliftYn)) return vi.heightM;
        // 미보유: 유효높이 사용. 파레트 차감 적용 시 설정값(ROLL_PALLET_DEDUCT_M)만큼 추가 차감
        double h = vi.effectiveHeightM;
        if (cp != null && cp.rollPalletApply) {
            h = Math.max(0, vi.heightM - cp.rollPalletDeductM);
        }
        return h;
    }

    private String boardQtyWarn(Map<String, Object> it, boolean boardInnerSplit) {
        String uom = str(it.get("UOMKEY"));
        double qty = dbl(it.get("QTSHPO"));
        if ("R".equals(uom) && qty != Math.floor(qty)) {
            String action = boardInnerSplit ? "속단위분할허용(BOARD_INNER_SPLIT_ALLOW=Y)" : "분할불가";
            return "[BOARD_BULK_INTEGER_ONLY] " + str(it.get("SKUKEY")) + " 수량=" + qty + "R (비정수) → " + action;
        }
        return null;
    }

    private String cval(Map<String, Map<String, Object>> C, String key, String def) {
        Map<String, Object> r = C.get(key);
        if (r == null) return def;
        Object v = r.get("CONST_VALUE");
        return v == null ? def : v.toString();
    }

    private ConstraintParams buildConstraintParams(Map<String, Map<String, Object>> C) {
        ConstraintParams cp = new ConstraintParams();
        cp.rollSingleKg     = cfloat(C, "ROLL_SINGLE_KG_FALLBACK",  600.0);
        cp.minFill          = cfloat(C, "MIN_FILL_RATIO",           0.0) / 100.0;
        cp.maxFill          = cfloat(C, "MAX_FILL_RATIO",           100.0) / 100.0;
        cp.penalty          = cfloat(C, "COST_PENALTY_OVER",        1.5);
        double bMin         = cfloat(C, "BOARD_MIN_FILL_RATIO",     -1.0);
        cp.boardMinFill     = bMin >= 0 ? bMin / 100.0 : cp.minFill;
        cp.allowSplit       = cbool(C, "ALLOW_SPLIT_ITEM",          true);
        cp.allowMixedLoad   = cbool(C, "ALLOW_MIXED_LOAD",          false);
        // ── 롤 최대 적재 단수: MAX_ROLL_STACK_TIER 우선, 미설정 시 중복키 ROLL_MAX_TIER 폴백 ──
        double maxStackVal  = cfloatMulti(C, 2.0, "MAX_ROLL_STACK_TIER", "ROLL_MAX_TIER");
        cp.maxStack         = (int) maxStackVal;
        // ── 판지 최대 높이: MAX_BOARD_HEIGHT_M 우선, 미설정 시 중복키 BOARD_HEIGHT_MAX_M 폴백 ──
        cp.maxBoardHeightM  = cfloatMulti(C, 2.4, "MAX_BOARD_HEIGHT_M", "BOARD_HEIGHT_MAX_M");
        cp.boardBulkIntOnly = cbool(C, "BOARD_BULK_INTEGER_ONLY",   true);
        cp.boardInnerSplit  = cbool(C, "BOARD_INNER_SPLIT_ALLOW",   true);

        // ══ 신규 반영 제약조건 ══════════════════════════════════════════

        // 진입 톤수 제한 (COMMON §1-1): 0 = 제한없음, >0 = 해당 톤수 초과 차량 배차 후보 제외
        cp.entryTonLimit    = cfloat(C, "ENTRY_TON_LIMIT",          0.0);

        // 고정차량 최우선 배차 (COMMON §1-3): Y = 고정차량 매핑 오더 사전할당
        cp.fixedVehPriority = cbool(C, "FIXED_VEH_PRIORITY",        false);

        // 그룹당 최대 배차 차량 수 (GLOBAL): 0/99 = 제한없음
        cp.maxVehPerGroup   = (int) cfloat(C, "MAX_VEHICLES_PER_GROUP", 99.0);

        // 3D 물리검증 토글 (ROLL/BOARD/MIX §*-4): N = 3D 검증 스킵
        cp.roll3dCheck      = cbool(C, "ROLL_3D_CHECK_YN",         true);
        cp.board3dCheck     = cbool(C, "BOARD_3D_CHECK_YN",        true);
        cp.mix3dCheck       = cbool(C, "MIX_3D_CHECK_YN",          true);
        // Dead Space 허용 비율(%) — 3D 검증 시 여유 마진으로 반영 (0 = 허용안함)
        cp.rollDeadSpacePct  = cfloat(C, "ROLL_3D_DEAD_SPACE_PCT",  0.0);
        cp.boardDeadSpacePct = cfloat(C, "BOARD_3D_DEAD_SPACE_PCT", 0.0);

        // 판지 CBM 검증 토글 (BOARD §3-1): N = CBM 이중검증(Double-Threshold) 스킵
        cp.boardCbmCheck    = cbool(C, "BOARD_CBM_CHECK_YN",       true);
        // 판지 CBM/중량 상한 비율(%) — Double-Threshold 임계치 (기본 100%)
        cp.boardMaxCbmRatio = cfloat(C, "BOARD_MAX_CBM_RATIO",     100.0) / 100.0;
        cp.boardMaxTonRatio = cfloat(C, "BOARD_MAX_TON_RATIO",     100.0) / 100.0;

        // 원지 높이 안전 여유 마진(m) (ROLL §2-2)
        cp.rollHeightMarginM = cfloat(C, "ROLL_HEIGHT_MARGIN_M",   0.0);
        // 파레트 차감 적용 여부 + 차감값(m) (ROLL §2-2): FORKLIFT_YN=N 납품처에 적용
        cp.rollPalletApply   = cbool(C, "ROLL_PALLET_APPLY_YN",    true);
        cp.rollPalletDeductM = cfloat(C, "ROLL_PALLET_DEDUCT_M",   0.15);

        return cp;
    }

    /** 여러 CONST_KEY를 우선순위대로 검사하여 최초로 명시된 값을 반환(중복/별칭 키 폴백) */
    private double cfloatMulti(Map<String, Map<String, Object>> C, double def, String... keys) {
        for (String k : keys) {
            Map<String, Object> r = C.get(k);
            if (r != null && r.get("CONST_VALUE") != null
                && !r.get("CONST_VALUE").toString().isBlank()) {
                try { return Double.parseDouble(r.get("CONST_VALUE").toString()); }
                catch (Exception ignored) { /* 다음 키 시도 */ }
            }
        }
        return def;
    }

    private double cfloat(Map<String, Map<String, Object>> C, String key, double def) {
        try { return Double.parseDouble(cval(C, key, String.valueOf(def))); }
        catch (Exception e) { return def; }
    }

    private boolean cbool(Map<String, Map<String, Object>> C, String key, boolean def) {
        String v = cval(C, key, def ? "Y" : "N");
        return "Y".equalsIgnoreCase(v);
    }

    private double parseVehicleWidth(String w) {
        try {
            String s = w.trim();
            if (s.contains("~")) return Double.parseDouble(s.split("~")[0].trim());
            return Double.parseDouble(s);
        } catch (Exception e) { return 2.4; }
    }

    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    // ── 산술 헬퍼 ─────────────────────────────────────────────────
    private static double dbl(Object v) {
        if (v == null) return 0.0;
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0.0; }
    }
    private static String str(Object v) { return v == null ? "" : v.toString().trim(); }
    private static long toLong(Object v, long def) {
        if (v == null) return def;
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return def; }
    }
    private static Integer toInt(Object v) {
        if (v == null) return null;
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }
    private static double round2(double v)  { return Math.round(v * 100.0)   / 100.0; }
    private static double round4(double v)  { return Math.round(v * 10000.0) / 10000.0; }

    // ════════════════════════════════════════════════════════════════
    //  내부 VO / 데이터 클래스
    // ════════════════════════════════════════════════════════════════

    private static class RollBin {
        List<Map<String, Object>> items = new ArrayList<>();
        double totalKg = 0;
        Map<String, Integer> v12 = new HashMap<>();
        Map<String, Integer> v3  = new HashMap<>();
    }

    private static class BoardBin {
        List<Map<String, Object>> items;
        double totalKg, totalH, totalCbm;
        String splitReason;
        BoardBin(List<Map<String, Object>> items, double kg, double h, double cbm, String reason) {
            this.items = items; this.totalKg = kg; this.totalH = h;
            this.totalCbm = cbm; this.splitReason = reason;
        }
    }

    private static class VehInfo {
        double heightM, palletHeightM, effectiveHeightM, widthM, lengthM, loadKg;
        String classCode = "";
        static final VehInfo EMPTY = new VehInfo();
    }

    private static class SkuInfo {
        double grswgt = 0, cubicm = 0;
        int wMm = 0, lMm = 0;
    }

    private static class PtnrInfo {
        String deadlineTime = "", forkliftYn = "", dynamicYn = "", maxTonLabel = "";
        double maxLoadKg = 0;
        static final PtnrInfo EMPTY = new PtnrInfo();
    }

    private static class InchMaps {
        Map<String, Map<String, Integer>> inch12 = new HashMap<>();
        Map<String, Map<String, Integer>> inch3  = new HashMap<>();
    }

    private static class ConstraintParams {
        double rollSingleKg = 600.0, minFill = 0.0, maxFill = 1.0;
        double penalty = 1.5, boardMinFill = 0.0, maxBoardHeightM = 2.4;
        int maxStack = 2;
        boolean allowSplit = true, allowMixedLoad = false;
        boolean boardBulkIntOnly = true, boardInnerSplit = true;

        // ── 신규 반영 제약조건 ──
        double  entryTonLimit    = 0.0;    // 진입 톤수 제한 (0=제한없음)
        boolean fixedVehPriority = false;  // 고정차량 최우선 배차
        int     maxVehPerGroup   = 99;     // 그룹당 최대 배차 차량 수
        boolean roll3dCheck      = true;   // 원지 3D 검증 활성
        boolean board3dCheck     = true;   // 판지 3D 검증 활성
        boolean mix3dCheck       = true;   // 혼적 3D 검증 활성
        double  rollDeadSpacePct  = 0.0;   // 원지 Dead Space 허용 비율(%)
        double  boardDeadSpacePct = 0.0;   // 판지 Dead Space 허용 비율(%)
        boolean boardCbmCheck    = true;   // 판지 CBM 이중검증 활성
        double  boardMaxCbmRatio = 1.0;    // 판지 CBM 상한 비율 (기본 100%)
        double  boardMaxTonRatio = 1.0;    // 판지 중량 상한 비율 (기본 100%)
        double  rollHeightMarginM = 0.0;   // 원지 높이 안전 여유 마진(m)
        boolean rollPalletApply   = true;  // 파레트 차감 적용 여부
        double  rollPalletDeductM = 0.15;  // 파레트 차감값(m)
    }

    // ════════════════════════════════════════════════════════════════
    //  3D 물리검증 엔진
    //
    //  ■ 원지(원통형 블록)
    //    - 단위 블록: 직경 D(mm) × 너비 W(mm) 의 원통
    //    - 차량 적재함 길이(L_car) 방향으로 원통 축 배치
    //    - 바닥 행 배치: 차량 너비(W_car)에 직경 D 기준 열(col) 수 = floor(W_car / D)
    //    - 단수(tier): floor(가용 높이 / D) 단까지 적층
    //    - 복수 SKU: 직경이 다른 롤은 같은 레이어에 혼재 불가 → 각 직경 그룹을 독립 레이어로 계획
    //    - 검증: 전체 롤 수 ≤ Σ(각 레이어의 tier × col × 바닥 점유 행 수)
    //
    //  ■ 판지(직육면체 블록)
    //    - 단위 블록: W(mm) × L(mm) × H(mm) (번들 1묶음 기준)
    //    - 차량 바닥(W_car × L_car)에 블록을 그리디 Shelf 배치
    //    - Shelf = 바닥에서 특정 Y(길이방향) 구간까지 채운 열
    //    - 블록 배치 시 회전 허용(W/L 교환): 차량 너비에 최적으로 맞는 방향 선택
    //    - 높이 누적: 같은 Shelf 내 블록의 최대 H = 해당 Shelf 점유 높이
    //    - 검증: 모든 번들 배치 후 Shelf 총 높이 ≤ 차량 가용 높이
    // ════════════════════════════════════════════════════════════════

    // ── 원지(원통) 3D 검증 결과 VO ───────────────────────────────────
    private static class RollPhysics3D {
        boolean fits;           // 차량에 물리적으로 적재 가능한가
        int     totalRolls;     // 검증 대상 총 롤 수
        int     maxCapacity;    // 해당 차량의 최대 수용 롤 수 (3D 기준)
        double  usedFloorRatio; // 바닥 점유율 (%)
        double  stackHeightM;   // 최고 단 높이 (m)
        String  summary;        // 요약 메시지
        List<String> layerNotes = new ArrayList<>(); // 레이어별 배치 상세

        static RollPhysics3D fail(String reason) {
            RollPhysics3D r = new RollPhysics3D();
            r.fits = false; r.summary = reason; return r;
        }
    }

    // ── 판지(직육면체) 3D 검증 결과 VO ──────────────────────────────
    private static class BoardPhysics3D {
        boolean fits;           // 차량에 물리적으로 적재 가능한가
        int     totalBundles;   // 검증 대상 총 번들 수
        double  usedFloorRatio; // 바닥 점유율 (%)
        double  maxHeightM;     // 최고 Shelf 높이 (m)
        double  usedVolM3;      // 적재 물품 총 체적 (m³)
        String  summary;        // 요약 메시지
        List<String> shelfNotes = new ArrayList<>();

        static BoardPhysics3D fail(String reason) {
            BoardPhysics3D r = new BoardPhysics3D();
            r.fits = false; r.summary = reason; return r;
        }
    }

    /**
     * 원지(원통형) 3D 물리검증
     *
     * @param items      원지 아이템 목록 (각 아이템에 SKUKEY, QTSHPO, KG_WEIGHT, WIDTH_MM 포함)
     * @param skumaMap   SKU 마스터 (GRSWGT, wMm 참조)
     * @param vi         배차 대상 차량 정보
     * @param cp         제약 파라미터 (maxStack, rollSingleKg)
     */
    private RollPhysics3D verifyRolls3D(List<Map<String, Object>> items,
                                         Map<String, SkuInfo> skumaMap,
                                         VehInfo vi, ConstraintParams cp) {
        double carWmm = vi.widthM  * 1000.0;   // 차량 너비 (mm)
        double carLmm = vi.lengthM * 1000.0;   // 차량 길이 (mm)
        double carHmm = vi.effectiveHeightM * 1000.0; // 차량 가용 높이 (mm)

        if (carWmm <= 0 || carLmm <= 0 || carHmm <= 0)
            return RollPhysics3D.fail("차량 치수 정보 없음 — 3D검증 스킵");

        // ── 1. 아이템별 원통 블록 파라미터 추출 ──────────────────────
        // 원통 축 = 차량 길이(Y) 방향으로 눕혀 적재
        // 직경 D, 너비(축 길이) W_roll
        //   W_roll: SHPDI.WIDTH_MM(=WIDTHW) → fallback: SKUMA.wMm → fallback: SKUKEY 파싱
        //   직경 D:  gsm/밀도/중량 역산 calcRollDiameter
        //
        // 레이어 그룹: {직경_반올림50mm → RollLayer}
        Map<Integer, RollLayer> layerMap = new LinkedHashMap<>(); // key = 직경 50mm 버킷

        int totalRolls = 0;
        for (Map<String, Object> it : items) {
            String sk  = str(it.get("SKUKEY"));
            if (!isRoll(sk) && !"R".equals(str(it.get("UOMKEY")))) continue;

            int qty = Math.max(1, (int) dbl(it.get("QTSHPO")));
            totalRolls += qty;

            // 너비(mm): SHPDI.WIDTH_MM 우선, 없으면 SKUMA.wMm, 없으면 SKUKEY 파싱
            double widthMm = dbl(it.get("WIDTH_MM"));
            if (widthMm <= 0) {
                SkuInfo sm = skumaMap.getOrDefault(sk, new SkuInfo());
                widthMm = sm.wMm > 0 ? sm.wMm : 0;
            }
            if (widthMm <= 0) {
                int[] dims = parseSkyueyDims(sk);
                if (dims != null) widthMm = dims[1]; // wmm
            }
            if (widthMm <= 0) widthMm = 1000.0; // 최후 fallback 1000mm
            final double finalWidthMm = widthMm; // 람다 캡처용 effectively-final 복사본

            // 직경(mm): KG_WEIGHT 또는 단위중량 기반 역산
            double kgPerRoll = itemRollKg(it, skumaMap, cp.rollSingleKg) / Math.max(qty, 1);
            Double dmmCalc = calcRollDiameter(kgPerRoll, sk);
            double diamMm  = (dmmCalc != null && dmmCalc > 0) ? dmmCalc : 800.0; // fallback 800mm

            // 직경 50mm 단위 버킷으로 그룹화 (소수점 오차 흡수)
            int bucket = (int)(Math.ceil(diamMm / 50.0) * 50);
            layerMap.computeIfAbsent(bucket, k -> new RollLayer(k, finalWidthMm))
                    .addRolls(qty, widthMm, diamMm);
        }

        if (totalRolls == 0) return RollPhysics3D.fail("원통형 롤 없음");

        // ── 2. 레이어별 적재 계획 ─────────────────────────────────────
        // 차량 길이 방향(Y축): 너비 widthMm 만큼 점유
        // 차량 너비 방향(X축): 직경 diamMm 만큼 열 점유 → cols = floor(carWmm / diamMm)
        // 차량 높이 방향(Z축): 직경 diamMm 만큼 단 점유 → maxTiers = min(cp.maxStack, floor(carHmm / diamMm))
        // 길이 점유: ceil(rolls / cols) 행 × widthMm → 총 길이 Σ
        RollPhysics3D result = new RollPhysics3D();
        result.totalRolls = totalRolls;

        double usedLengthMm = 0.0; // Y축 점유 길이 누적
        int    totalCap     = 0;

        for (Map.Entry<Integer, RollLayer> e : layerMap.entrySet()) {
            RollLayer layer = e.getValue();
            double diamMm  = layer.repDiamMm;
            double widthMm = layer.repWidthMm;

            int cols     = Math.max(1, (int)(carWmm / diamMm));
            int maxTiers = Math.max(1, Math.min(cp.maxStack, (int)(carHmm / diamMm)));
            int perRow   = cols; // 한 행(X×Z 단면)에 세울 수 있는 개수 = cols × 1tier에서 추가로 높이 방향 = cols × maxTiers
            int perSlice = cols * maxTiers; // 너비 1 widthMm 깊이 점유 슬라이스당 수용 롤 수

            // 이 레이어에 필요한 길이(Y) = ceil(layer.totalRolls / perSlice) × widthMm
            int rows = (int) Math.ceil((double) layer.totalRolls / perSlice);
            double layerLengthMm = rows * widthMm;
            usedLengthMm += layerLengthMm;

            int layerCap = rows * perSlice;
            totalCap += layerCap;

            result.layerNotes.add(String.format(
                "[3D-원지] 직경그룹%dmm: 롤%d개 / 차량내 배치=%d열×%d단×%d행 / Y축점유=%.0fmm",
                (int)diamMm, layer.totalRolls, cols, maxTiers, rows, layerLengthMm));
        }

        result.maxCapacity  = totalCap;
        result.stackHeightM = layerMap.values().stream()
            .mapToDouble(l -> l.repDiamMm * Math.min(cp.maxStack, (int)(carHmm / l.repDiamMm)))
            .max().orElse(0) / 1000.0;
        result.usedFloorRatio = carLmm > 0 ? Math.min(100.0, usedLengthMm / carLmm * 100.0) : 0.0;
        // Dead Space 허용 비율(ROLL_3D_DEAD_SPACE_PCT) 반영: 기본 2% + 설정 비율만큼 여유 허용
        double rollMargin = 1.02 + (cp.rollDeadSpacePct / 100.0);
        result.fits = (usedLengthMm <= carLmm * rollMargin);

        result.summary = String.format(
            "[3D-원지검증] %s / 총%d롤 / Y축점유%.0fmm/%.0fmm(%.0f%%) / 최고단높이%.2fm / %s",
            result.fits ? "적재가능" : "적재불가(차량업그레이드필요)",
            totalRolls, usedLengthMm, carLmm, result.usedFloorRatio,
            result.stackHeightM, result.fits ? "OK" : "OVERFLOW");
        return result;
    }

    /** 원지 레이어 그룹 보조 클래스 */
    private static class RollLayer {
        int    bucket;
        double repDiamMm  = 0;  // 대표 직경 (평균)
        double repWidthMm = 0;  // 대표 너비 (평균)
        int    totalRolls = 0;
        int    sampleCount = 0;

        RollLayer(int bucket, double firstWidth) {
            this.bucket = bucket; this.repWidthMm = firstWidth;
        }
        void addRolls(int qty, double widthMm, double diamMm) {
            totalRolls += qty;
            // 가중 평균으로 대표값 갱신
            repDiamMm  = (repDiamMm  * sampleCount + diamMm  * qty) / (sampleCount + qty);
            repWidthMm = (repWidthMm * sampleCount + widthMm * qty) / (sampleCount + qty);
            sampleCount += qty;
        }
    }

    /**
     * 판지(직육면체) 3D 물리검증 — Shelf 배치 알고리즘
     *
     * 차량 적재함: 너비(X=W_car) × 길이(Y=L_car) × 높이(Z=H_car)
     * 각 번들 블록: w × l × h (mm)
     *   w, l: SKUMA.ASKL04/05 또는 SKUKEY 파싱, 회전 허용(w↔l)
     *   h   : calcBoardHeight() — gsm/밀도/매수 기반 역산
     *
     * Shelf 배치:
     *   - 현재 Shelf의 Y 좌표부터 블록 l 만큼 전진
     *   - 해당 Shelf 내 X 방향으로 블록 w 씩 채움
     *   - Shelf 너비(X) 초과 → 다음 Shelf 행으로 이동 (Y 좌표 += l)
     *   - Shelf 총 높이(Z) = 해당 Shelf 내 블록 최대 h
     *   - 모든 Shelf의 최대 Z > H_car → 배치 불가 (차량 업그레이드 필요)
     */
    private BoardPhysics3D verifyBoards3D(List<Map<String, Object>> items,
                                           Map<String, SkuInfo> skumaMap,
                                           VehInfo vi, ConstraintParams cp) {
        double carWmm = vi.widthM  * 1000.0;
        double carLmm = vi.lengthM * 1000.0;
        double carHmm = vi.effectiveHeightM * 1000.0;

        if (carWmm <= 0 || carLmm <= 0 || carHmm <= 0)
            return BoardPhysics3D.fail("차량 치수 정보 없음 — 3D검증 스킵");

        // ── 1. 아이템별 번들 블록 추출 ────────────────────────────────
        // 번들 1묶음 = GRSWGT 기준 1 연(ream) 또는 1 roll 단위
        // 총 번들 수 = qty / grswgt 또는 qty (UOM에 따라)
        List<BoardBlock> blocks = new ArrayList<>();
        int totalBundles = 0;
        double totalVolMm3 = 0;

        for (Map<String, Object> it : items) {
            String sk = str(it.get("SKUKEY"));
            SkuInfo sm = skumaMap.getOrDefault(sk, new SkuInfo());

            // 치수 추출: SKUMA.ASKL04(W), ASKL05(L) → fallback: SKUKEY 파싱
            double wMm = sm.wMm > 0 ? sm.wMm : 0;
            double lMm = sm.lMm > 0 ? sm.lMm : 0;
            if (wMm <= 0 || lMm <= 0) {
                int[] bd = parseBoardDims(sk);
                if (bd != null) { wMm = bd[0]; lMm = bd[1]; }
            }
            // 출고예정정보 WIDTH_MM, LENGTH 필드로 보완 (SHPDI 컬럼)
            if (wMm <= 0) { double v = dbl(it.get("WIDTH_MM")); if (v > 0) wMm = v; }
            if (lMm <= 0) { double v = dbl(it.get("LENGTH"));   if (v > 0) lMm = v; }
            if (wMm <= 0) wMm = 1000.0;
            if (lMm <= 0) lMm = 1000.0;

            // 높이 h: 번들(묶음) 1단위 높이 (mm)
            double hM  = calcBoardHeight(it, skumaMap);     // 번들 1개 높이(m)
            double hMm = Math.max(1.0, hM * 1000.0);
            hMm = Math.min(hMm, cp.maxBoardHeightM * 1000.0); // 상한 clamp

            // 번들 수 = 총 kg / 묶음 단중
            double totalKg = boardKg(it, skumaMap);
            double grswgt  = sm.grswgt > 0 ? sm.grswgt : dbl(it.get("GRSWGT"));
            int    bundles = grswgt > 0 ? Math.max(1, (int) Math.ceil(totalKg / grswgt)) : 1;

            totalBundles += bundles;
            totalVolMm3  += wMm * lMm * hMm * bundles;

            blocks.add(new BoardBlock(wMm, lMm, hMm, bundles, sk));
        }

        if (totalBundles == 0) return BoardPhysics3D.fail("판지 번들 없음");

        // ── 2. 블록 정렬: 높이 내림차순 (높은 블록 먼저 → 바닥 안정성) ─
        blocks.sort(Comparator.comparingDouble((BoardBlock b) -> b.hMm).reversed());

        // ── 3. Shelf 배치 시뮬레이션 ──────────────────────────────────
        BoardPhysics3D result = new BoardPhysics3D();
        result.totalBundles = totalBundles;
        result.usedVolM3    = totalVolMm3 / 1e9;

        // Shelf 상태: 현재 Y 위치, 현재 X 사용량, 현재 Shelf 최대 높이
        double curY       = 0.0;  // 현재 Shelf Y 시작 위치
        double curX       = 0.0;  // 현재 Shelf X 사용량
        double curShelfL  = 0.0;  // 현재 Shelf 길이(L 방향)
        double curShelfH  = 0.0;  // 현재 Shelf 최대 높이
        double maxZ       = 0.0;  // 전체 최고 높이
        boolean overflow  = false;

        for (BoardBlock blk : blocks) {
            int remaining = blk.count;
            while (remaining > 0) {
                // 블록 방향 선택: w를 X방향, l을 Y방향 / 또는 l을 X방향, w를 Y방향
                // → 차량 너비(X)에 더 잘 맞는 방향 선택
                double bw, bl;
                if (blk.wMm <= carWmm && blk.lMm <= carWmm) {
                    // 둘 다 가능: 너비 낭비가 적은 방향
                    double rem1 = carWmm - (Math.floor(carWmm / blk.wMm) * blk.wMm);
                    double rem2 = carWmm - (Math.floor(carWmm / blk.lMm) * blk.lMm);
                    bw = rem1 <= rem2 ? blk.wMm : blk.lMm;
                    bl = rem1 <= rem2 ? blk.lMm : blk.wMm;
                } else if (blk.wMm <= carWmm) {
                    bw = blk.wMm; bl = blk.lMm;
                } else if (blk.lMm <= carWmm) {
                    bw = blk.lMm; bl = blk.wMm;
                } else {
                    // 블록 단면이 차량 너비보다 큼 → 배치 불가
                    result.shelfNotes.add(String.format(
                        "[3D-판지] %s 번들크기(%.0f×%.0fmm)가 차량너비(%.0fmm) 초과 → 수동검토", blk.skukey, blk.wMm, blk.lMm, carWmm));
                    overflow = true;
                    remaining = 0;
                    continue;
                }

                // 현재 Shelf에 X방향으로 몇 개 들어가는지
                int colsInShelf = (int)(carWmm / bw);
                if (colsInShelf <= 0) { overflow = true; remaining = 0; continue; }

                // 새 Shelf가 필요한가? (X 다 찼거나, 첫 배치)
                if (curX + bw > carWmm + 0.5) {
                    // 다음 Shelf 행으로
                    curY += curShelfL;
                    maxZ = Math.max(maxZ, curShelfH);
                    curX = 0; curShelfL = bl; curShelfH = blk.hMm;
                }
                if (curShelfL <= 0) curShelfL = bl;

                // Y축 범위 초과 검사
                if (curY + bl > carLmm + 0.5) {
                    overflow = true;
                    result.shelfNotes.add(String.format(
                        "[3D-판지] %s: Y축 점유 초과(%.0fmm > 차량길이%.0fmm) → 수용불가", blk.skukey, curY + bl, carLmm));
                    remaining = 0;
                    continue;
                }

                // 이 Shelf에 배치 가능한 수량
                int canPlace = colsInShelf;
                int place    = Math.min(canPlace, remaining);
                curX += bw * place;
                curShelfH = Math.max(curShelfH, blk.hMm);
                remaining -= place;

                // X 가득 찬 경우 다음 Shelf 준비
                if (curX + bw > carWmm + 0.5 && remaining > 0) {
                    curY += curShelfL;
                    maxZ = Math.max(maxZ, curShelfH);
                    curX = 0; curShelfL = bl; curShelfH = blk.hMm;
                }
            }
        }
        maxZ = Math.max(maxZ, curShelfH);

        result.maxHeightM = maxZ / 1000.0;
        result.usedFloorRatio = carLmm > 0 ? Math.min(100.0, (curY + curShelfL) / carLmm * 100.0) : 0.0;

        // 높이 초과 여부 — Dead Space 허용 비율(BOARD_3D_DEAD_SPACE_PCT) 반영
        double boardMargin = 1.02 + (cp.boardDeadSpacePct / 100.0);
        if (maxZ > carHmm * boardMargin) overflow = true;

        result.fits = !overflow;
        result.summary = String.format(
            "[3D-판지검증] %s / 총%d번들 / Y축점유%.0fmm/%.0fmm / 최고높이%.2fm/%.2fm / 바닥점유%.0f%% / %s",
            result.fits ? "적재가능" : "적재불가(차량업그레이드필요)",
            totalBundles, curY + curShelfL, carLmm,
            result.maxHeightM, vi.effectiveHeightM,
            result.usedFloorRatio, result.fits ? "OK" : "OVERFLOW");
        return result;
    }

    /** 판지 번들 블록 보조 클래스 */
    private static class BoardBlock {
        double wMm, lMm, hMm;
        int    count;
        String skukey;
        BoardBlock(double w, double l, double h, int cnt, String sk) {
            wMm = w; lMm = l; hMm = h; count = cnt; skukey = sk;
        }
    }
}
