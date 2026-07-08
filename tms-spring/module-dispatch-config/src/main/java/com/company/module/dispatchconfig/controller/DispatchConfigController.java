package com.company.module.dispatchconfig.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.dispatchconfig.entity.*;
import com.company.module.dispatchconfig.service.DispatchConfigService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping({"/dispatch-config-api", "/api"})
@RequiredArgsConstructor
public class DispatchConfigController {

    private final DispatchConfigService service;

    // ── Objective ────────────────────────────────────────────────
    @GetMapping("/dispatch-objective")
    public ResponseEntity<ApiResponse<List<DispatchObjective>>> getObjectives() {
        return ResponseEntity.ok(ApiResponse.success(service.getObjectiveList()));
    }

    @GetMapping("/dispatch-objective/active")
    public ResponseEntity<ApiResponse<DispatchObjective>> getActiveObjective() {
        return ResponseEntity.ok(ApiResponse.success(
                service.getActiveObjective().orElse(null)));
    }

    @PostMapping("/dispatch-objective/save")
    public ResponseEntity<ApiResponse<DispatchObjective>> saveObjective(@RequestBody ObjectiveRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                service.saveObjective(req.getObjId(), req.getObjCode(), req.getObjNm(),
                        req.getObjIcon(), req.getObjAlgo(), req.getObjDesc(),
                        req.getSortSeq(), req.getActiveYn())));
    }

    @PostMapping("/dispatch-objective/activate")
    public ResponseEntity<ApiResponse<Void>> activateObjective(@RequestBody ObjectiveRequest req) {
        service.activateObjective(req.getObjId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/dispatch-objective/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteObjective(@PathVariable Long id) {
        service.deleteObjective(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Profile ──────────────────────────────────────────────────
    @GetMapping("/dispatch-profile")
    public ResponseEntity<ApiResponse<List<DispatchProfile>>> getProfiles() {
        return ResponseEntity.ok(ApiResponse.success(service.getProfileList()));
    }

    @GetMapping("/dispatch-profile/{id}")
    public ResponseEntity<ApiResponse<DispatchProfile>> getProfile(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.getProfile(id)));
    }

    @PostMapping("/dispatch-profile/save")
    public ResponseEntity<ApiResponse<DispatchProfile>> saveProfile(@RequestBody ProfileRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                service.saveProfile(req.getProfileId(), req.getProfileNm(),
                        req.getProfileDesc(), req.getSortSeq())));
    }

    @DeleteMapping("/dispatch-profile/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProfile(@PathVariable Long id) {
        service.deleteProfile(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Constraint ───────────────────────────────────────────────
    @GetMapping("/dispatch-constraint")
    public ResponseEntity<ApiResponse<List<DispatchConstraint>>> getConstraints(
            @RequestParam Long profileId) {
        return ResponseEntity.ok(ApiResponse.success(service.getConstraintList(profileId)));
    }

    @PostMapping("/dispatch-constraint/save")
    public ResponseEntity<ApiResponse<DispatchConstraint>> saveConstraint(@RequestBody ConstraintRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                service.saveConstraint(req.getOwnrky(), req.getConstraintId(), req.getProfileId(),
                        req.getConstraintType(), req.getConstraintKey(),
                        req.getConstraintVal(), req.getSortSeq())));
    }

    @DeleteMapping("/dispatch-constraint/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteConstraint(@PathVariable Long id,
                                                               @RequestParam String ownrky) {
        service.deleteConstraint(ownrky, id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── ConstSet ─────────────────────────────────────────────────
    @GetMapping("/dispatch-const-set")
    public ResponseEntity<ApiResponse<List<DispatchConstSet>>> getConstSets(@RequestParam Long profileId) {
        return ResponseEntity.ok(ApiResponse.success(service.getConstSetList(profileId)));
    }

    @GetMapping("/dispatch-const-set/by-type")
    public ResponseEntity<ApiResponse<List<DispatchConstSet>>> getConstSetsByType(
            @RequestParam Long profileId, @RequestParam String constType) {
        return ResponseEntity.ok(ApiResponse.success(service.getConstSetByType(profileId, constType)));
    }

    @PostMapping("/dispatch-const-set/save")
    public ResponseEntity<ApiResponse<DispatchConstSet>> saveConstSet(@RequestBody ConstSetRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                service.saveConstSet(req.getOwnrky(), req.getConstId(), req.getProfileId(),
                        req.getConstType(), req.getCartype(), req.getRegion(), req.getConstVal(),
                        req.getIsDynamic(), req.getForkliftYn(), req.getEntryTon())));
    }

    @DeleteMapping("/dispatch-const-set/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteConstSet(@PathVariable Long id,
                                                             @RequestParam String ownrky) {
        service.deleteConstSet(ownrky, id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Inner Request DTOs ────────────────────────────────────────
    @Getter @Setter
    static class ObjectiveRequest {
        private Long    objId;
        private String  objCode;
        private String  objNm;
        private String  objIcon;
        private String  objAlgo;
        private String  objDesc;
        private Integer sortSeq;
        private String  activeYn;
    }

    @Getter @Setter
    static class ProfileRequest {
        private Long    profileId;
        private String  profileNm;
        private String  profileDesc;
        private Integer sortSeq;
    }

    @Getter @Setter
    static class ConstraintRequest {
        private Long    constraintId;
        private Long    profileId;
        private String  ownrky;
        private String  constraintType;
        private String  constraintKey;
        private String  constraintVal;
        private Integer sortSeq;
    }

    @Getter @Setter
    static class ConstSetRequest {
        private Long    constId;
        private Long    profileId;
        private String  ownrky;
        private String  constType;
        private String  cartype;
        private String  region;
        private String  constVal;
        private Integer isDynamic;
        private String  forkliftYn;
        private Double  entryTon;
    }
}
