package com.company.module.dispatchconfig.service;

import com.company.module.dispatchconfig.entity.DispatchObjective;
import com.company.module.dispatchconfig.entity.DispatchProfile;
import com.company.module.dispatchconfig.repository.DispatchObjectiveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DispatchConfigService {

    private final DispatchObjectiveRepository objectiveRepo;

    @PersistenceContext
    private EntityManager em;

    // ── 목적식 목록 (Flask api_obj_list) ──
    public List<DispatchObjective> getObjectiveList() {
        return objectiveRepo.findAllByOrderBySortSeqAscObjIdAsc();
    }

    // ── 활성 목적식 (Flask api_obj_active) ──
    public Optional<DispatchObjective> getActiveObjective() {
        return objectiveRepo.findByActiveYn("Y");
    }

    // ── 목적식 저장 (Flask api_obj_save) ──
    @Transactional
    public void saveObjective(Map<String, Object> data) {
        String today  = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Object objIdObj = data.get("OBJ_ID");
        String code = str(data.get("OBJ_CODE")).toUpperCase();
        String nm   = str(data.get("OBJ_NM"));
        if (code.isEmpty() || nm.isEmpty()) throw new IllegalArgumentException("OBJ_CODE, OBJ_NM 필수");

        if (objIdObj != null) {
            Long objId = Long.parseLong(objIdObj.toString());
            DispatchObjective obj = objectiveRepo.findById(objId)
                .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(
                    com.company.core.common.exception.ErrorCode.C404));
            obj.update(code, nm, str(data.get("OBJ_ICON")), str(data.get("OBJ_ALGO")),
                       str(data.get("OBJ_DESC")), toInt(data.get("SORT_SEQ")), str(data.get("ACTIVE_YN")));
        } else {
            DispatchObjective obj = DispatchObjective.builder()
                .objCode(code).objNm(nm)
                .objIcon(str(data.get("OBJ_ICON")))
                .objAlgo(str(data.get("OBJ_ALGO")))
                .objDesc(str(data.get("OBJ_DESC")))
                .sortSeq(toInt(data.get("SORT_SEQ")))
                .activeYn(str(data.get("ACTIVE_YN")))
                .credat(today).lmodat(today)
                .build();
            objectiveRepo.save(obj);
        }
    }

    // ── 목적식 삭제 (Flask api_obj_delete) ──
    @Transactional
    public void deleteObjective(Long objId) {
        objectiveRepo.deleteById(objId);
    }

    // ── 목적식 활성화 (Flask api_obj_activate) – 단일 활성 보장 ──
    @Transactional
    public void activateObjective(Long objId) {
        objectiveRepo.deactivateAll();
        DispatchObjective obj = objectiveRepo.findById(objId)
            .orElseThrow(() -> new com.company.core.common.exception.EntityNotFoundException(
                com.company.core.common.exception.ErrorCode.C404));
        obj.activate();
    }

    // ── 배차제약 프로파일 목록 (Flask api_dcon_profiles) ──
    public List<Object[]> getProfileList() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
            "SELECT * FROM ds_dispatch_profile ORDER BY PROFILE_ID"
        ).getResultList();
        return rows;
    }

    // ── 배차제약 목록 (Flask api_dcon_list) ──
    public List<Object[]> getConstraintList(Long profileId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
            "SELECT * FROM ds_dispatch_constraint WHERE PROFILE_ID = ? ORDER BY CONST_ID"
        ).setParameter(1, profileId).getResultList();
        return rows;
    }

    // ── dispatch-const-set 목록 (Flask api_set_list) ──
    public List<Object[]> getConstSetList() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
            "SELECT * FROM ds_dispatch_const_set ORDER BY SET_ID"
        ).getResultList();
        return rows;
    }

    // ── dispatch-const-set 전체 (Flask api_set_full) ──
    public Map<String, Object> getConstSetFull(Integer setId) {
        @SuppressWarnings("unchecked")
        List<Object[]> items = em.createNativeQuery(
            "SELECT * FROM ds_dispatch_const_set_item WHERE SET_ID = ? ORDER BY ITEM_ID"
        ).setParameter(1, setId).getResultList();
        @SuppressWarnings("unchecked")
        List<Object[]> cartypes = em.createNativeQuery(
            "SELECT * FROM ds_dispatch_const_cartype WHERE SET_ID = ? ORDER BY CARTYPE"
        ).setParameter(1, setId).getResultList();
        @SuppressWarnings("unchecked")
        List<Object[]> regions = em.createNativeQuery(
            "SELECT * FROM ds_dispatch_const_region WHERE SET_ID = ? ORDER BY REGION_CD"
        ).setParameter(1, setId).getResultList();
        return Map.of("items", items, "cartypes", cartypes, "regions", regions);
    }

    private String str(Object o) { return o == null ? "" : o.toString().strip(); }
    private int toInt(Object o)  { try { return Integer.parseInt(o.toString()); } catch (Exception e) { return 0; } }
}
