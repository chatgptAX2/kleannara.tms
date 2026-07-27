package com.company.module.vehicle.service;

import com.company.module.vehicle.dto.*;
import com.company.module.vehicle.entity.tms.DsVehicle;
import com.company.module.vehicle.entity.wms.Vhcma;
import com.company.module.vehicle.repository.tms.DsVehicleRepository;
import com.company.module.vehicle.repository.wms.VhcmaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 차량유형 / 차량마스터 서비스
 * Flask: api_carclass / api_ds_vehicle / api_vehicle_* 대응
 *
 * ■ DataSource 라우팅
 *   - em    (wmsPU, Oracle KNRAWMS): CMCDM, CMCDV, WAHMA, VHCMA
 *   - tmsEm (tmsPU, MariaDB TMS):   DS_VEHICLE
 *
 *   ※ VhcmaRepository → WmsJpaConfig (wmsPU, Oracle KNRAWMS)
 *      DsVehicleRepository → TmsJpaConfig (tmsPU, MariaDB)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "wmsTransactionManager")
public class VehicleService {

    private final DsVehicleRepository dsVehicleRepo;
    private final VhcmaRepository     vhcmaRepo;

    /** Oracle WMS — KNRAWMS.CMCDM / CMCDV / WAHMA / VHCMA */
    @PersistenceContext(unitName = "wmsPU")
    private EntityManager em;

    /** MariaDB TMS — DS_VEHICLE (차종 직접 쿼리) */
    @PersistenceContext(unitName = "tmsPU")
    private EntityManager tmsEm;

    // ──────────────────────────────────────────────────────────────────────────
    // DS_VEHICLE 목록 (Flask api_ds_vehicle)
    // DsVehicleRepository → TmsJpaConfig → MariaDB 자동 라우팅
    // ──────────────────────────────────────────────────────────────────────────
    public List<DsVehicleResponse> getDsVehicleList() {
        List<DsVehicle> list = dsVehicleRepo.findAllByOrderBySortSeqAsc();
        List<DsVehicleResponse> result = new ArrayList<>();
        for (DsVehicle v : list) {
            double loadTon = v.getLoadTon() == null ? 0 : v.getLoadTon();
            Double loadKg  = loadTon > 0 ? Math.round(loadTon * 1000.0 * 10.0) / 10.0 : null;

            // WIDTH_M 숫자 파싱 (범위 표현 처리)
            String wRaw = v.getWidthM() == null ? "" : v.getWidthM();
            double widthNum = 2.4;
            try {
                widthNum = wRaw.contains("~")
                    ? Double.parseDouble(wRaw.split("~")[0].strip())
                    : Double.parseDouble(wRaw);
            } catch (Exception ignored) {}

            result.add(DsVehicleResponse.builder()
                .carclassCd(v.getCarclassCd())
                .cartype(v.getCartype())
                .lengthM(v.getLengthM())
                .widthM(v.getWidthM())
                .widthMNum(widthNum)
                .heightM(v.getHeightM())
                .loadTon(loadTon)
                .loadKg(loadKg)
                .sortSeq(v.getSortSeq())
                .palletHeightM(v.getPalletHeightM())
                .palletCnt(v.getPalletCnt())
                .longAxisYn(v.getLongAxisYn())
                .inch12Lt300(v.getInch12Lt300())
                .inch12Ge300(v.getInch12Ge300())
                .inch3Lt300(v.getInch3Lt300())
                .inch3Ge300(v.getInch3Ge300())
                .defaultVehCnt(v.getDefaultVehCnt())
                .upddat(v.getUpddat())
                .updusr(v.getUpdusr())
                .build());
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Carclass 통합 조회 (Flask api_carclass)
    // CMCDV → Oracle em / DsVehicleRepository → MariaDB 자동
    // ──────────────────────────────────────────────────────────────────────────
    public Map<String, Object> getCarclass() {
        // TMS_CARCLASS10 공통코드 (Oracle KNRAWMS.CMCDV → em)
        @SuppressWarnings("unchecked")
        List<Object[]> ccRows = em.createNativeQuery("""
            SELECT CMCDVL, CDESC1, USARG1, USARG2, USARG3, USARG4, USARG5
            FROM KNRAWMS.CMCDV WHERE CMCDKY = 'TMS_CARCLASS10' ORDER BY CMCDVL
            """).getResultList();

        List<DsVehicle> vhcList = dsVehicleRepo.findAllByOrderBySortSeqAsc();
        Map<String, DsVehicle> vhcMap = new LinkedHashMap<>();
        for (DsVehicle v : vhcList) vhcMap.put(v.getCarclassCd(), v);

        // merged
        List<Map<String, Object>> merged = new ArrayList<>();
        List<Map<String, Object>> ccList = new ArrayList<>();

        for (Object[] r : ccRows) {
            String cmcdvl = str(r[0]);
            String cdesc1 = str(r[1]);
            DsVehicle vhc = vhcMap.get(cmcdvl);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("CMCDVL", cmcdvl);
            m.put("CDESC1", cdesc1);
            m.put("USARG1", str(r[2]));
            m.put("USARG2", str(r[3]));
            m.put("USARG3", str(r[4]));
            m.put("USARG4", str(r[5]));
            m.put("USARG5", str(r[6]));
            m.put("USARG6", vhc != null && vhc.getLengthM() != null ? vhc.getLengthM().toString() : "");
            m.put("USARG7", vhc != null && vhc.getWidthM() != null  ? vhc.getWidthM() : "");
            m.put("USARG8", vhc != null && vhc.getHeightM() != null ? vhc.getHeightM().toString() : "");
            m.put("CARCLASS_CD", vhc != null ? vhc.getCarclassCd() : null);
            m.put("CARTYPE",   vhc != null ? vhc.getCartype()   : null);
            m.put("LENGTH_M",  vhc != null ? vhc.getLengthM()   : null);
            m.put("WIDTH_M",   vhc != null ? vhc.getWidthM()    : null);
            m.put("HEIGHT_M",  vhc != null ? vhc.getHeightM()   : null);
            m.put("LOAD_TON",  vhc != null ? vhc.getLoadTon()   : null);
            m.put("SORT_SEQ",  vhc != null ? vhc.getSortSeq()   : null);
            m.put("UPDDAT",    vhc != null ? vhc.getUpddat()     : null);
            m.put("UPDUSR",    vhc != null ? vhc.getUpdusr()     : null);
            m.put("HAS_VHC",   vhc != null);
            merged.add(m);

            Map<String, Object> cc = new LinkedHashMap<>();
            cc.put("CMCDVL", cmcdvl); cc.put("CDESC1", cdesc1);
            cc.put("USARG1", str(r[2])); cc.put("USARG2", str(r[3]));
            cc.put("USARG3", str(r[4])); cc.put("USARG4", str(r[5]));
            cc.put("USARG5", str(r[6]));
            ccList.add(cc);
        }

        List<DsVehicleResponse> vhcResp = getDsVehicleList();
        return Map.of("carclasses", ccList, "vehicles", vhcResp, "merged", merged);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 제품군별 차량톤수 공통코드 (Flask api_carclass_by_product)
    // CMCDM, CMCDV → Oracle em / DsVehicleRepository → MariaDB 자동
    // ──────────────────────────────────────────────────────────────────────────
    public Map<String, Object> getCarclassByProduct(String productGroup) {
        Map<String, String> cmcdkyMap = Map.of("10", "TMS_CARCLASS10", "20", "TMS_CARCLASS20");
        String cmcdky = cmcdkyMap.getOrDefault(productGroup == null ? "" : productGroup.strip(), "TMS_CARCLASS10");

        // Oracle KNRAWMS.CMCDM → em
        @SuppressWarnings("unchecked")
        Object[] mRow = (Object[]) em.createNativeQuery(
            "SELECT USARL1, USARL2, USARL3, USARL4, USARL5 FROM KNRAWMS.CMCDM WHERE CMCDKY=?")
            .setParameter(1, cmcdky)
            .getResultStream().findFirst().orElse(null);

        List<Map<String, Object>> header = new ArrayList<>();
        if (mRow != null) {
            String[] lblKeys = {"USARL1","USARL2","USARL3","USARL4","USARL5"};
            String[] argKeys = {"USARG1","USARG2","USARG3","USARG4","USARG5"};
            for (int i = 0; i < lblKeys.length; i++) {
                String lbl = str(mRow[i]);
                if (!lbl.isEmpty()) header.add(Map.of("col", argKeys[i], "label", lbl));
            }
        }

        // Oracle KNRAWMS.CMCDV → em
        @SuppressWarnings("unchecked")
        List<Object[]> vRows = em.createNativeQuery(
            "SELECT CMCDVL, CDESC1, USARG1, USARG2, USARG3, USARG4, USARG5 FROM KNRAWMS.CMCDV WHERE CMCDKY=? ORDER BY CMCDVL")
            .setParameter(1, cmcdky)
            .getResultList();

        Map<String, DsVehicle> vhcMap = new LinkedHashMap<>();
        dsVehicleRepo.findAll().forEach(v -> vhcMap.put(v.getCarclassCd(), v));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] r : vRows) {
            String cmcdvl = str(r[0]);
            DsVehicle vhc = vhcMap.get(cmcdvl);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("CMCDVL", cmcdvl); d.put("CDESC1", str(r[1]));
            d.put("USARG1", str(r[2])); d.put("USARG2", str(r[3]));
            d.put("USARG3", str(r[4])); d.put("USARG4", str(r[5]));
            d.put("USARG5", str(r[6]));
            d.put("USARG6", vhc != null && vhc.getLengthM() != null ? vhc.getLengthM().toString() : "");
            d.put("USARG7", vhc != null && vhc.getWidthM() != null  ? vhc.getWidthM() : "");
            d.put("USARG8", vhc != null && vhc.getHeightM() != null ? vhc.getHeightM().toString() : "");
            rows.add(d);
        }
        return Map.of("cmcdky", cmcdky, "header", header, "rows", rows);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Carclass / DS_VEHICLE 저장 (Flask api_carclass_save)
    // CMCDV 업데이트 → Oracle em(wmsPU) / DsVehicle CUD → MariaDB Repository 자동
    // ──────────────────────────────────────────────────────────────────────────
    @Transactional(transactionManager = "wmsTransactionManager")
    public void saveCarclass(VehicleSaveRequest req) {
        String nowdt = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String nowtm = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));

        if ("carclass".equals(req.getTable())) {
            String key = req.getCmcdvl();
            if (key == null || key.isBlank()) throw new IllegalArgumentException("CMCDVL 필수");
            // Oracle KNRAWMS.CMCDV 업데이트 → em
            em.createNativeQuery("""
                UPDATE KNRAWMS.CMCDV SET CDESC1=?, USARG1=?, USARG2=?, USARG3=?, USARG4=?, USARG5=?,
                LMODAT=?, LMOTIM=?, LMOUSR=?
                WHERE CMCDKY='TMS_CARCLASS10' AND CMCDVL=?
                """)
              .setParameter(1, req.getCdesc1())
              .setParameter(2, req.getUsarg1())
              .setParameter(3, req.getUsarg2())
              .setParameter(4, req.getUsarg3())
              .setParameter(5, req.getUsarg4())
              .setParameter(6, req.getUsarg5())
              .setParameter(7, nowdt).setParameter(8, nowtm).setParameter(9, "WEB")
              .setParameter(10, key)
              .executeUpdate();

        } else if ("vehicle".equals(req.getTable())) {
            String carclassCd = req.getCarclassCd();
            if (carclassCd == null || carclassCd.isBlank()) {
                // CARTYPE으로 자동 보완 (MariaDB DsVehicleRepository → TmsJpaConfig 자동)
                Optional<DsVehicle> opt = dsVehicleRepo.findByCartype(req.getCartype());
                carclassCd = opt.map(DsVehicle::getCarclassCd).orElse(null);
            }
            if (carclassCd == null || carclassCd.isBlank())
                throw new IllegalArgumentException("CARCLASS_CD 필수");

            Optional<DsVehicle> existing = dsVehicleRepo.findById(carclassCd);
            if (existing.isPresent()) {
                // ── 부분 업데이트(Partial Update) ──────────────────────────
                // "가용차량대수(DEFAULT_VEH_CNT)"만 수정하는 단건 저장처럼
                // 일부 필드만 전송되는 경우, 미전송(null) 필드는 기존 값을 보존해야
                // 길이/폭/높이/톤수 등이 null로 덮어써지는 데이터 손상을 방지할 수 있다.
                DsVehicle cur = existing.get();
                cur.update(
                    coalesce(req.getCartype(),       cur.getCartype()),
                    coalesce(req.getLengthM(),        cur.getLengthM()),
                    coalesce(req.getWidthM(),         cur.getWidthM()),
                    coalesce(req.getHeightM(),        cur.getHeightM()),
                    coalesce(req.getLoadTon(),        cur.getLoadTon()),
                    coalesce(req.getSortSeq(),        cur.getSortSeq()),
                    coalesce(req.getPalletHeightM(),  cur.getPalletHeightM()),
                    coalesce(req.getPalletCnt(),      cur.getPalletCnt()),
                    coalesce(req.getLongAxisYn(),     cur.getLongAxisYn()),
                    coalesce(req.getInch12Lt300(),    cur.getInch12Lt300()),
                    coalesce(req.getInch12Ge300(),    cur.getInch12Ge300()),
                    coalesce(req.getInch3Lt300(),     cur.getInch3Lt300()),
                    coalesce(req.getInch3Ge300(),     cur.getInch3Ge300()),
                    coalesce(req.getDefaultVehCnt(),  cur.getDefaultVehCnt()),
                    nowdt, "WEB"
                );
            } else {
                int nextSeq = dsVehicleRepo.nextSortSeq();
                DsVehicle newV = DsVehicle.builder()
                    .carclassCd(carclassCd)
                    .cartype(req.getCartype())
                    .lengthM(req.getLengthM())
                    .widthM(req.getWidthM())
                    .heightM(req.getHeightM())
                    .loadTon(req.getLoadTon())
                    .sortSeq(req.getSortSeq() != null ? req.getSortSeq() : nextSeq)
                    .palletHeightM(req.getPalletHeightM() != null ? req.getPalletHeightM() : 0.0)
                    .palletCnt(req.getPalletCnt())
                    .longAxisYn(req.getLongAxisYn() != null ? req.getLongAxisYn() : "N")
                    .inch12Lt300(req.getInch12Lt300())
                    .inch12Ge300(req.getInch12Ge300())
                    .inch3Lt300(req.getInch3Lt300())
                    .inch3Ge300(req.getInch3Ge300())
                    .defaultVehCnt(req.getDefaultVehCnt())
                    .upddat(nowdt).updusr("WEB")
                    .build();
                dsVehicleRepo.save(newV);
            }

        } else if ("vehicle_delete".equals(req.getTable())) {
            String carclassCd = req.getCarclassCd();
            if (carclassCd == null || carclassCd.isBlank())
                throw new IllegalArgumentException("CARCLASS_CD 필수");
            dsVehicleRepo.deleteById(carclassCd);

        } else {
            throw new IllegalArgumentException("알 수 없는 table: " + req.getTable());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // VHCMA 차량 목록 (Flask api_vehicle_list)
    // VhcmaRepository.searchPage() → WmsJpaConfig → Oracle KNRAWMS 자동
    // WAHMA(창고) → Oracle em
    // ──────────────────────────────────────────────────────────────────────────
    public Map<String, Object> getVhcmaList(VhcmaSearchRequest req) {
        int page = req.getPage() == null ? 1 : req.getPage();
        int size = req.getSize() == null ? 50 : req.getSize();
        Pageable pageable = PageRequest.of(page - 1, size);

        // VhcmaRepository → WmsJpaConfig → Oracle KNRAWMS (Oracle LIKE '%'||?||'%' 적용)
        Page<Object[]> pageResult = vhcmaRepo.searchPage(
            nullIfBlank(req.getShipPoint()), nullIfBlank(req.getProductGroup()),
            nullIfBlank(req.getDeliveryZone()), nullIfBlank(req.getCarrier()),
            nullIfBlank(req.getVehicleType()), nullIfBlank(req.getVehicleKind()),
            nullIfBlank(req.getVehicleClass()), nullIfBlank(req.getVehicleNo()),
            pageable
        );

        // 창고 목록 (Oracle KNRAWMS.WAHMA → em)
        @SuppressWarnings("unchecked")
        List<Object[]> shipPoints = em.createNativeQuery(
            "SELECT WAREKY, NAME01 FROM KNRAWMS.WAHMA WHERE DELMAK=' ' OR DELMAK='' ORDER BY WAREKY"
        ).getResultList();

        List<Map<String, Object>> spList = new ArrayList<>();
        for (Object[] r : shipPoints) spList.add(Map.of("value", str(r[0]), "label", str(r[1])));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total",       pageResult.getTotalElements());
        result.put("page",        page);
        result.put("size",        size);
        result.put("pages",       pageResult.getTotalPages());
        result.put("rows",        pageResult.getContent());
        result.put("ship_points", spList);
        result.put("prod_groups", vhcmaRepo.findDistinctProductGroups());
        result.put("zones",       vhcmaRepo.findDistinctDeliveryZones());
        result.put("carriers",    vhcmaRepo.findDistinctCarriers());
        result.put("vtypes",      vhcmaRepo.findDistinctVehicleTypes());
        result.put("vkinds",      vhcmaRepo.findDistinctVehicleKinds());
        result.put("vclasses",    vhcmaRepo.findDistinctVehicleClasses());
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // VHCMA 상세 (Flask api_vehicle_detail)
    // VhcmaRepository → WmsJpaConfig → Oracle KNRAWMS 자동
    // ──────────────────────────────────────────────────────────────────────────
    public Vhcma getVhcmaDetail(String vehicleNo, String ownrky) {
        return vhcmaRepo.findByVehicleNoAndOwnrky(vehicleNo, ownrky == null ? "KN" : ownrky)
            .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(
                com.company.core.common.exception.ErrorCode.VEHICLE_NOT_FOUND));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // VHCMA 저장 (Flask api_vehicle_save)
    // vhcma → Oracle em (wmsPU) — VHCMA는 KNRAWMS Oracle 테이블
    // ──────────────────────────────────────────────────────────────────────────
    @Transactional(transactionManager = "wmsTransactionManager")
    public String saveVhcma(VhcmaSaveRequest req) {
        String vno    = req.getVehicleNo() == null ? "" : req.getVehicleNo().strip();
        String ownrky = req.getOwnrky() == null ? "KN" : req.getOwnrky().strip();
        if (vno.isEmpty()) throw new IllegalArgumentException("차량번호(VEHICLE_NO)는 필수입니다");

        String nowdt = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String nowtm = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));

        if (vhcmaRepo.existsByVehicleNoAndOwnrky(vno, ownrky)) {
            // Oracle KNRAWMS.VHCMA UPDATE → em (wmsPU)
            em.createNativeQuery("""
                UPDATE KNRAWMS.VHCMA SET SHIP_POINT=?,PRODUCT_GROUP=?,DELIVERY_ZONE=?,CARRIER=?,
                VEHICLE_TYPE=?,VEHICLE_KIND=?,VEHICLE_CLASS=?,CARTYPE=?,CARCLASS_CD=?,
                DRIVER_NAME=?,CONTACT_NO=?,PALLET_QTY=?,FLOOR_TYPE=?,USE_YN=?,OPERABLE_YN=?,
                FIX_YN=?,DLV_TIME_FROM=?,DLV_TIME_TO=?,VEHICLE_YEAR=?,
                DELIVERY_CUSTOMER_1=?,DELIVERY_CUSTOMER_2=?,DEL_YN=?,
                LMODAT=?,LMOTIM=?,LMOUSR=?
                WHERE VEHICLE_NO=? AND OWNRKY=?
                """)
              .setParameter(1,  req.getShipPoint())
              .setParameter(2,  req.getProductGroup())
              .setParameter(3,  req.getDeliveryZone())
              .setParameter(4,  req.getCarrier())
              .setParameter(5,  req.getVehicleType())
              .setParameter(6,  req.getVehicleKind())
              .setParameter(7,  req.getVehicleClass())
              .setParameter(8,  req.getCartype())
              .setParameter(9,  req.getCarclassCd())
              .setParameter(10, req.getDriverName())
              .setParameter(11, req.getContactNo())
              .setParameter(12, req.getPalletQty())
              .setParameter(13, req.getFloorType())
              .setParameter(14, req.getUseYn())
              .setParameter(15, req.getOperableYn())
              .setParameter(16, req.getFixYn())
              .setParameter(17, req.getDlvTimeFrom())
              .setParameter(18, req.getDlvTimeTo())
              .setParameter(19, req.getVehicleYear())
              .setParameter(20, req.getDeliveryCustomer1())
              .setParameter(21, req.getDeliveryCustomer2())
              .setParameter(22, req.getDelYn())
              .setParameter(23, nowdt).setParameter(24, nowtm).setParameter(25, "WEB")
              .setParameter(26, vno).setParameter(27, ownrky)
              .executeUpdate();
            return "updated";
        } else {
            // Oracle KNRAWMS.VHCMA INSERT → em (wmsPU)
            em.createNativeQuery("""
                INSERT INTO KNRAWMS.VHCMA
                (VEHICLE_NO,OWNRKY,SHIP_POINT,PRODUCT_GROUP,DELIVERY_ZONE,CARRIER,
                 VEHICLE_TYPE,VEHICLE_KIND,VEHICLE_CLASS,CARTYPE,CARCLASS_CD,
                 DRIVER_NAME,CONTACT_NO,PALLET_QTY,FLOOR_TYPE,USE_YN,OPERABLE_YN,FIX_YN,
                 DLV_TIME_FROM,DLV_TIME_TO,VEHICLE_YEAR,
                 DELIVERY_CUSTOMER_1,DELIVERY_CUSTOMER_2,DEL_YN,
                 CREDAT,CRETIM,CREUSR,LMODAT,LMOTIM,LMOUSR)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)
              .setParameter(1, vno).setParameter(2, ownrky)
              .setParameter(3,  req.getShipPoint())
              .setParameter(4,  req.getProductGroup())
              .setParameter(5,  req.getDeliveryZone())
              .setParameter(6,  req.getCarrier())
              .setParameter(7,  req.getVehicleType())
              .setParameter(8,  req.getVehicleKind())
              .setParameter(9,  req.getVehicleClass())
              .setParameter(10, req.getCartype())
              .setParameter(11, req.getCarclassCd())
              .setParameter(12, req.getDriverName())
              .setParameter(13, req.getContactNo())
              .setParameter(14, req.getPalletQty())
              .setParameter(15, req.getFloorType())
              .setParameter(16, req.getUseYn())
              .setParameter(17, req.getOperableYn())
              .setParameter(18, req.getFixYn())
              .setParameter(19, req.getDlvTimeFrom())
              .setParameter(20, req.getDlvTimeTo())
              .setParameter(21, req.getVehicleYear())
              .setParameter(22, req.getDeliveryCustomer1())
              .setParameter(23, req.getDeliveryCustomer2())
              .setParameter(24, req.getDelYn())
              .setParameter(25, nowdt).setParameter(26, nowtm).setParameter(27, "WEB")
              .setParameter(28, nowdt).setParameter(29, nowtm).setParameter(30, "WEB")
              .executeUpdate();
            return "created";
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // VHCMA 삭제 (Flask api_vehicle_delete) – 소프트 삭제 (DEL_YN='Y')
    // Vhcma entity는 WmsJpaConfig 관리 → wmsTransactionManager 사용
    // ──────────────────────────────────────────────────────────────────────────
    @Transactional(transactionManager = "wmsTransactionManager")
    public void deleteVhcma(String vehicleNo, String ownrky) {
        String nowdt = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String nowtm = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        Vhcma v = getVhcmaDetail(vehicleNo, ownrky);
        v.delete(nowdt, nowtm, "WEB");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // util
    // ──────────────────────────────────────────────────────────────────────────
    private String str(Object o) { return o == null ? "" : o.toString().strip(); }
    private String nullIfBlank(String s) { return (s == null || s.isBlank()) ? null : s.strip(); }

    /** 부분 업데이트용: 신규값이 null이면 기존값을 유지한다. */
    private <T> T coalesce(T incoming, T current) { return incoming != null ? incoming : current; }
}
