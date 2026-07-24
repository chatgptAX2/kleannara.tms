package com.company.module.wms.controller;

import com.company.module.wms.service.AutoDispatchService;
import com.company.module.wms.service.DispatchConfigApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 배차설정 API Controller (Flask /api/dispatch-objective/*, /api/dispatch-constraint/*, /api/dispatch-const-set/*)
 * 기존 module-dispatch-config의 스키마와 달리 Flask 실제 스키마를 완전히 지원
 * URL: /api/dispatch-objective/**, /api/dispatch-constraint/**, /api/dispatch-const-set/**
 *
 * /api/dispatch-constraint/auto → AutoDispatchService (완전 구현 FFD/BFD/MIN_COST 알고리즘)
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DispatchConfigApiController {

    private final DispatchConfigApiService svc;
    private final AutoDispatchService      autoDispatch;

    // ── 목적식 (DS_DISPATCH_OBJECTIVE) ──────────────────────────

    @GetMapping("/dispatch-objective/list")
    public ResponseEntity<Map<String, Object>> objList() {
        return ResponseEntity.ok(svc.objList());
    }

    @PostMapping("/dispatch-objective/save")
    public ResponseEntity<Map<String, Object>> objSave(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(svc.objSave(body));
    }

    @PostMapping("/dispatch-objective/delete")
    public ResponseEntity<Map<String, Object>> objDelete(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(svc.objDelete(body));
    }

    @PostMapping("/dispatch-objective/activate")
    public ResponseEntity<Map<String, Object>> objActivate(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(svc.objActivate(body));
    }

    @GetMapping("/dispatch-objective/active")
    public ResponseEntity<Map<String, Object>> objActive() {
        return ResponseEntity.ok(svc.objActive());
    }

    // ── 제약조건 세트 (DS_DISPATCH_CONST_SET) ────────────────────

    @GetMapping("/dispatch-const-set/list")
    public ResponseEntity<Map<String, Object>> setList() {
        return ResponseEntity.ok(svc.setList());
    }

    @PostMapping("/dispatch-const-set/save")
    public ResponseEntity<Map<String, Object>> setSave(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(svc.setSave(body));
    }

    @PostMapping("/dispatch-const-set/delete")
    public ResponseEntity<Map<String, Object>> setDelete(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(svc.setDelete(body));
    }

    @GetMapping("/dispatch-const-set/items")
    public ResponseEntity<Map<String, Object>> setItems(@RequestParam(required = false) Integer setId) {
        return ResponseEntity.ok(svc.setItems(setId));
    }

    @GetMapping("/dispatch-const-set/full")
    public ResponseEntity<Map<String, Object>> setFull(@RequestParam(name = "set_id", required = false) Integer setId) {
        return ResponseEntity.ok(svc.setFull(setId));
    }

    @GetMapping("/dispatch-const-set/vehicle-types")
    public ResponseEntity<Map<String, Object>> setVehicleTypes() {
        return ResponseEntity.ok(svc.setVehicleTypes());
    }

    @PostMapping("/dispatch-const-set/cartype/save")
    public ResponseEntity<Map<String, Object>> setCartypeSave(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(svc.setCartypeSave(body));
    }

    @GetMapping("/dispatch-const-set/region/list")
    public ResponseEntity<Map<String, Object>> setRegionList() {
        return ResponseEntity.ok(svc.setRegionList());
    }

    @PostMapping("/dispatch-const-set/region/save")
    public ResponseEntity<Map<String, Object>> setRegionSave(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(svc.setRegionSave(body));
    }

    @GetMapping("/dispatch-const-set/entry-ton/list")
    public ResponseEntity<Map<String, Object>> setEntryTonList() {
        return ResponseEntity.ok(svc.setEntryTonList());
    }

    @PostMapping("/dispatch-const-set/entry-ton/save")
    public ResponseEntity<Map<String, Object>> setEntryTonSave(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(svc.setEntryTonSave(body));
    }

    @GetMapping("/dispatch-const-set/forklift/list")
    public ResponseEntity<Map<String, Object>> setForkliftList() {
        return ResponseEntity.ok(svc.setForkliftList());
    }

    @PostMapping("/dispatch-const-set/forklift/save")
    public ResponseEntity<Map<String, Object>> setForkliftSave(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(svc.setForkliftSave(body));
    }

    @GetMapping("/dispatch-const-set/dynamic/list")
    public ResponseEntity<Map<String, Object>> setDynamicList() {
        return ResponseEntity.ok(svc.setDynamicList());
    }

    @PostMapping("/dispatch-const-set/dynamic/save")
    public ResponseEntity<Map<String, Object>> setDynamicSave(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(svc.setDynamicSave(body));
    }

    @PostMapping("/dispatch-const-set/items/save")
    public ResponseEntity<Map<String, Object>> setItemsSave(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(svc.setItemsSave(body));
    }

    // ── 제약조건 프로파일 (DS_DISPATCH_PROFILE) ──────────────────

    @GetMapping("/dispatch-constraint/profiles")
    public ResponseEntity<Map<String, Object>> profiles() {
        return ResponseEntity.ok(svc.profiles());
    }

    @PostMapping("/dispatch-constraint/profiles/save")
    public ResponseEntity<Map<String, Object>> profileSave(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(svc.profileSave(body));
    }

    @PostMapping("/dispatch-constraint/profiles/delete")
    public ResponseEntity<Map<String, Object>> profileDelete(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(svc.profileDelete(body));
    }

    @PostMapping("/dispatch-constraint/profiles/link-set")
    public ResponseEntity<Map<String, Object>> profileLinkSet(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(svc.profileLinkSet(body));
    }

    @GetMapping("/dispatch-constraint/all")
    public ResponseEntity<Map<String, Object>> constraintAll() {
        return ResponseEntity.ok(svc.constraintAll());
    }

    @GetMapping("/dispatch-constraint/list")
    public ResponseEntity<Map<String, Object>> constraintList(
            @RequestParam(required = false) Long profileId) {
        return ResponseEntity.ok(svc.constraintList(profileId));
    }

    @PostMapping("/dispatch-constraint/save")
    public ResponseEntity<Map<String, Object>> constraintSave(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(svc.constraintSave(body));
    }

    @PostMapping("/dispatch-constraint/delete")
    public ResponseEntity<Map<String, Object>> constraintDelete(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(svc.constraintDelete(body));
    }

    @PostMapping("/dispatch-constraint/copy-profile")
    public ResponseEntity<Map<String, Object>> constraintCopyProfile(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(svc.constraintCopyProfile(body));
    }

    @GetMapping("/dispatch-constraint/meta")
    public ResponseEntity<Map<String, Object>> constraintMeta() {
        return ResponseEntity.ok(svc.constraintMeta());
    }

    /**
     * 제약 조건 프로파일 기반 자동배차 (목적식 선택)
     * body: { profile_id, items: [...] } 또는 { date, ptnrky }
     * 목적식: MIN_VEHICLES (FFD) / MAX_FILL (BFD) / MIN_COST (ROUTE_COST)
     */
    @PostMapping("/dispatch-constraint/auto")
    public ResponseEntity<Map<String, Object>> constraintAuto(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(autoDispatch.runAuto(body));
    }
}
