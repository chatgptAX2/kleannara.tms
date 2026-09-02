package com.company.module.wms.controller;

import com.company.module.wms.service.StrategyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 배차전략 Controller
 * Flask: /api/dispatch/strategy, /api/dispatch/strategy/save, /api/dispatch/simulate
 *        /api/carclass, /api/carclass-by-product, /api/ds-vehicle, /api/carclass/save
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyService strategyService;

    // ── 배차전략 (DS_INCH12/DS_INCH3) ────────────────────────────

    @GetMapping("/dispatch/strategy")
    public ResponseEntity<Map<String, Object>> getStrategy() {
        return ResponseEntity.ok(strategyService.getStrategy());
    }

    @PostMapping("/dispatch/strategy/save")
    public ResponseEntity<Map<String, Object>> saveStrategy(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(strategyService.saveStrategy(body));
    }

    @PostMapping("/dispatch/simulate")
    public ResponseEntity<Map<String, Object>> simulate(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(strategyService.simulate(body));
    }

    // ── 차종 (DS_VEHICLE / CMCDV) ─────────────────────────────────

    @GetMapping("/carclass")
    public ResponseEntity<Map<String, Object>> getCarClass() {
        return ResponseEntity.ok(strategyService.getCarClass());
    }

    @GetMapping("/carclass-by-product")
    public ResponseEntity<Map<String, Object>> getCarClassByProduct(
            @RequestParam(required = false) String skukey,
            @RequestParam(name = "product_group", required = false) String productGroup) {
        return ResponseEntity.ok(strategyService.getCarClassByProduct(skukey, productGroup));
    }

    @GetMapping("/ds-vehicle")
    public ResponseEntity<Map<String, Object>> getDsVehicle() {
        return ResponseEntity.ok(strategyService.getDsVehicle());
    }

    @PostMapping("/carclass/save")
    public ResponseEntity<Map<String, Object>> saveCarClass(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(strategyService.saveCarClass(body));
    }
}
