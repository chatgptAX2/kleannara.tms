package com.company.module.dispatchconfig.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.dispatchconfig.entity.DispatchObjective;
import com.company.module.dispatchconfig.service.DispatchConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 배차제약 설정 Controller
 * Flask: /api/dispatch-objective/*, /api/dispatch-constraint/*, /api/dispatch-const-set/* 대응
 * URL prefix: /dispatch-config-api
 */
@RestController
@RequestMapping("/dispatch-config-api")
@RequiredArgsConstructor
public class DispatchConfigController {

    private final DispatchConfigService dispatchConfigService;

    // ── 목적식 ──────────────────────────────────────────────────────────

    @GetMapping("/dispatch-objective/list")
    public ResponseEntity<ApiResponse<Object>> getObjectiveList() {
        List<DispatchObjective> rows = dispatchConfigService.getObjectiveList();
        return ResponseEntity.ok(ApiResponse.success(Map.of("ok", true, "rows", rows)));
    }

    @GetMapping("/dispatch-objective/active")
    public ResponseEntity<ApiResponse<Object>> getActiveObjective() {
        Optional<DispatchObjective> opt = dispatchConfigService.getActiveObjective();
        return ResponseEntity.ok(ApiResponse.success(opt.orElse(null)));
    }

    @PostMapping("/dispatch-objective/save")
    public ResponseEntity<ApiResponse<Object>> saveObjective(@RequestBody Map<String, Object> body) {
        dispatchConfigService.saveObjective(body);
        return ResponseEntity.ok(ApiResponse.success(Map.of("ok", true)));
    }

    @PostMapping("/dispatch-objective/delete")
    public ResponseEntity<ApiResponse<Object>> deleteObjective(@RequestBody Map<String, Object> body) {
        Long objId = Long.parseLong(body.get("OBJ_ID").toString());
        dispatchConfigService.deleteObjective(objId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("ok", true)));
    }

    @PostMapping("/dispatch-objective/activate")
    public ResponseEntity<ApiResponse<Object>> activateObjective(@RequestBody Map<String, Object> body) {
        Long objId = Long.parseLong(body.get("OBJ_ID").toString());
        dispatchConfigService.activateObjective(objId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("ok", true)));
    }

    // ── 배차제약 프로파일 ──────────────────────────────────────────────

    @GetMapping("/dispatch-constraint/profiles")
    public ResponseEntity<ApiResponse<Object>> getProfiles() {
        return ResponseEntity.ok(ApiResponse.success(
            Map.of("ok", true, "rows", dispatchConfigService.getProfileList())
        ));
    }

    @GetMapping("/dispatch-constraint/list")
    public ResponseEntity<ApiResponse<Object>> getConstraintList(
            @RequestParam Long profileId) {
        return ResponseEntity.ok(ApiResponse.success(
            Map.of("ok", true, "rows", dispatchConfigService.getConstraintList(profileId))
        ));
    }

    // ── const-set ─────────────────────────────────────────────────────

    @GetMapping("/dispatch-const-set/list")
    public ResponseEntity<ApiResponse<Object>> getConstSetList() {
        return ResponseEntity.ok(ApiResponse.success(
            Map.of("ok", true, "rows", dispatchConfigService.getConstSetList())
        ));
    }

    @GetMapping("/dispatch-const-set/full")
    public ResponseEntity<ApiResponse<Object>> getConstSetFull(
            @RequestParam Integer setId) {
        return ResponseEntity.ok(ApiResponse.success(dispatchConfigService.getConstSetFull(setId)));
    }
}
