package com.company.module.wms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 자동배차 알고리즘 Service — Flask api_dcon_auto() 완전 Java 포팅
 *
 * 지원 목적식:
 *   MIN_VEHICLES : 차량 수 최소화 — FFD BinPacking (First-Fit Decreasing)
 *   MAX_FILL     : 적재율 최대화 — BFD BinPacking (Best-Fit Decreasing)
 *   MIN_COST     : 운송비 최소화 — ROUTE_COST 기반 최저비용 차종 선택
 *
 * SKUKEY 구조: [0:2]=prefix [2:5]=inchCode [5:8]=gsm [8]='-' [9:13]=width_mm [13:17]=length_mm
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoDispatchService {

    private final JdbcTemplate jdbc;

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

        // 제약 조건 로드
        List<Map<String, Object>> constRows = jdbc.queryForList(
            "SELECT * FROM DS_DISPATCH_CONST WHERE PROFILE_ID=? AND ACTIVE_YN='Y' ORDER BY SORT_SEQ",
            pid
        );
        // 전역 제약 맵
        Map<String, Map<String, Object>> C = new LinkedHashMap<>();
        Map<String, Map<String, Map<String, Object>>> Cbt = new HashMap<>();  // TARGET_ID별
        Set<String> allowedCartypes = new HashSet<>();

        for (Map<String, Object> r : constRows) {
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
                        allowedCartypes, ptnrInfoMap, Cbt);
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
                validCars = getValidCars(dptnky, carOrder, vehInfo, allowedCartypes, ptnrInfoMap, Cbt);
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
                searchBins = bins.stream()
                    .sorted(Comparator.comparingDouble(b -> bigCap - b.totalKg))
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

            // 높이 검사 + 차량 업그레이드
            List<String> notes = new ArrayList<>(splitNotesPre);
            if (isDynBlocked) notes.add("[동적배차불가] DYNAMIC_YN=N → 고정노선 전용 오더");
            else if ("Y".equals(pi.dynamicYn)) notes.add("[동적배차가능] DYNAMIC_YN=Y");

            for (Map<String, Object> it : b.items) {
                String sk = str(it.get("SKUKEY"));
                if (!isRoll(sk)) continue;
                Double dmm = calcRollDiameter(cp.rollSingleKg, sk);
                if (dmm == null) continue;

                int    actualStack = Math.min(cp.maxStack, 3);
                double stackH      = dmm / 1000.0 * actualStack;
                double effH        = rollEffH(vehCar, pi.forkliftYn, vehInfo);

                if (stackH > effH) {
                    boolean upgraded = false;
                    for (Map<String, Object> c : carOrder) {
                        String ct = str(c.get("CARTYPE"));
                        if (sortKey(ct, carOrder) >= sortKey(vehCar, carOrder)) continue;
                        if (rollEffH(ct, pi.forkliftYn, vehInfo) >= stackH) {
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
        List<BoardBin> vehListB = new ArrayList<>();
        List<Map<String, Object>> curB = new ArrayList<>();
        double curKg = 0, curH = 0, curCbm = 0;

        for (Map<String, Object> it : boardItems) {
            double qtyKg  = boardKg(it, skumaMap);
            double itemH  = Math.min(calcBoardHeight(it, skumaMap), cp.maxBoardHeightM);
            double itemCbm = getItemCbm(it, skumaMap);

            boolean ko = !curB.isEmpty() && (curKg + qtyKg > bigCap);
            boolean co = !curB.isEmpty() && itemCbm > 0 && bigCbm > 0 && (curCbm + itemCbm > bigCbm);
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

    private Map<String, Object> loadProfile(Integer profileId) {
        List<Map<String, Object>> rows;
        if (profileId != null) {
            rows = jdbc.queryForList("SELECT * FROM DS_DISPATCH_PROFILE WHERE PROFILE_ID=?", profileId);
        } else {
            rows = jdbc.queryForList(
                "SELECT * FROM DS_DISPATCH_PROFILE WHERE ACTIVE_YN='Y' ORDER BY PROFILE_ID LIMIT 1");
        }
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Map<String, Object>> fetchItemsByDate(String yyyymmdd, String ptnrkyFilter) {
        StringBuilder sql = new StringBuilder(
            "SELECT h.SHPOKY, h.DPTNKY, b.NAME01 AS DPTNM, h.RQSHPD, " +
            "       i.SHPOIT, i.SKUKEY, i.QTSHPO, i.DESC01 AS SKUNM, " +
            "       i.GRSWGT, i.WGTUNT, i.UOMKEY, i.LENGTH, i.WIDTHW AS WIDTH_MM, i.HEIGHT " +
            "FROM SHPDH h JOIN SHPDI i ON h.SHPOKY=i.SHPOKY " +
            "LEFT JOIN BZPTN b ON h.DPTNKY=b.PTNRKY AND b.PTNRTY='CT' " +
            "WHERE h.RQSHPD=?"
        );
        List<Object> args = new ArrayList<>();
        args.add(yyyymmdd);
        if (!ptnrkyFilter.isEmpty()) { sql.append(" AND h.DPTNKY=?"); args.add(ptnrkyFilter); }
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    private List<Map<String, Object>> loadCarOrder() {
        return jdbc.queryForList(
            "SELECT v.CARTYPE, v.LOAD_TON, v.SORT_SEQ FROM DS_VEHICLE v " +
            "LEFT JOIN CMCDV c ON c.CMCDKY='TMS_CARCLASS10' AND c.CMCDVL=v.CARCLASS_CD " +
            "WHERE COALESCE(c.USARG1,'Y')='Y' ORDER BY v.SORT_SEQ DESC"
        );
    }

    private Map<String, VehInfo> loadVehInfo() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT v.CARTYPE, v.LENGTH_M, v.WIDTH_M, v.HEIGHT_M, v.LOAD_TON, " +
            "       v.PALLET_HEIGHT_M, v.CARCLASS_CD FROM DS_VEHICLE v " +
            "LEFT JOIN CMCDV c ON c.CMCDKY='TMS_CARCLASS10' AND c.CMCDVL=v.CARCLASS_CD " +
            "WHERE COALESCE(c.USARG1,'Y')='Y'"
        );
        Map<String, VehInfo> result = new HashMap<>();
        for (Map<String, Object> r : rows) {
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

    private InchMaps loadInchMaps() {
        InchMaps m = new InchMaps();
        List<Map<String, Object>> i12 = jdbc.queryForList("SELECT CARTYPE,GRM_COND,MAX_COUNT FROM DS_INCH12");
        List<Map<String, Object>> i3  = jdbc.queryForList("SELECT CARTYPE,GRM_COND,MAX_COUNT FROM DS_INCH3");
        for (Map<String, Object> r : i12)
            m.inch12.computeIfAbsent(str(r.get("CARTYPE")), k -> new HashMap<>())
                    .put(str(r.get("GRM_COND")), (int) dbl(r.get("MAX_COUNT")));
        for (Map<String, Object> r : i3)
            m.inch3.computeIfAbsent(str(r.get("CARTYPE")), k -> new HashMap<>())
                   .put(str(r.get("GRM_COND")), (int) dbl(r.get("MAX_COUNT")));
        return m;
    }

    private Map<String, SkuInfo> loadSkumaMap() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT SKUKEY, GRSWGT, ASKL04, ASKL05, CUBICM FROM SKUMA WHERE MTYPE='P'"
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

    private Map<String, Map<String, Double>> loadRouteCostMap(String costDate) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT rc.PTNRKY, cc.CDESC1 AS CARTYPE, rc.COST, rc.CARCLASS " +
            "FROM ROUTE_COST rc " +
            "LEFT JOIN CMCDV cc ON cc.CMCDKY='TMS_CARCLASS10' AND cc.CMCDVL=rc.CARCLASS " +
            "WHERE rc.DATE_START<=? AND rc.DATE_END>=?", costDate, costDate
        );
        Map<String, Map<String, Double>> result = new HashMap<>();
        for (Map<String, Object> r : rows) {
            String ct = str(r.get("CARTYPE"));
            if (!ct.isEmpty()) {
                result.computeIfAbsent(str(r.get("PTNRKY")), k -> new HashMap<>())
                      .put(ct, dbl(r.get("COST")));
            }
        }
        return result;
    }

    private Map<String, PtnrInfo> loadPtnrInfo(List<String> dptnkyList,
                                                 Map<String, VehInfo> vehInfo) {
        if (dptnkyList.isEmpty()) return Collections.emptyMap();
        String ph = dptnkyList.stream().map(x -> "?").collect(Collectors.joining(","));
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT PTNRKY,DEADLINE_TIME,FORKLIFT_YN,MAX_TON,DYNAMIC_YN " +
            "FROM BZPTN_DETAIL WHERE PTNRKY IN (" + ph + ") AND PTNRTY='CT'",
            dptnkyList.toArray()
        );
        // CARCLASS10 코드명 매핑
        List<Map<String, Object>> ccRows = jdbc.queryForList(
            "SELECT CMCDVL,CDESC1 FROM CMCDV WHERE CMCDKY='TMS_CARCLASS10'"
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
                List<Map<String, Object>> prows = jdbc.queryForList(
                    "SELECT PTNRKY, POSTCD FROM BZPTN WHERE PTNRKY IN (" + ph + ") AND PTNRTY='CT'",
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
            Map<String, Map<String, Map<String, Object>>> Cbt) {
        List<Map<String, Object>> cars = carOrder.stream()
            .filter(c -> vehInfo.getOrDefault(str(c.get("CARTYPE")), VehInfo.EMPTY).loadKg > 0)
            .collect(Collectors.toList());

        if (!allowedCartypes.isEmpty()) {
            cars = cars.stream()
                .filter(c -> allowedCartypes.contains(str(c.get("CARTYPE"))))
                .collect(Collectors.toList());
        }

        if (!dptnky.isEmpty()) {
            PtnrInfo pi = ptnrInfoMap.getOrDefault(dptnky, PtnrInfo.EMPTY);
            if (pi.maxLoadKg > 0) {
                List<Map<String, Object>> filtered = cars.stream()
                    .filter(c -> vehInfo.getOrDefault(str(c.get("CARTYPE")), VehInfo.EMPTY).loadKg <= pi.maxLoadKg)
                    .collect(Collectors.toList());
                if (!filtered.isEmpty()) cars = filtered;
            }
            // FORCE_CARTYPE 납품처별 제약
            Map<String, Object> forceCr = Cbt.getOrDefault(dptnky, Collections.emptyMap())
                                            .get("FORCE_CARTYPE");
            if (forceCr != null) {
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

    private double rollEffH(String cartype, String forkliftYn, Map<String, VehInfo> vehInfo) {
        VehInfo vi = vehInfo.getOrDefault(cartype, VehInfo.EMPTY);
        return "Y".equals(forkliftYn) ? vi.heightM : vi.effectiveHeightM;
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
        cp.maxStack         = (int) cfloat(C, "MAX_ROLL_STACK_TIER", 2.0);
        cp.maxBoardHeightM  = cfloat(C, "MAX_BOARD_HEIGHT_M",       2.4);
        cp.boardBulkIntOnly = cbool(C, "BOARD_BULK_INTEGER_ONLY",   true);
        cp.boardInnerSplit  = cbool(C, "BOARD_INNER_SPLIT_ALLOW",   true);
        return cp;
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
    }
}
