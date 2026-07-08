package com.company.module.vehicle.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.vehicle.dto.*;
import com.company.module.vehicle.entity.Vhcma;
import com.company.module.vehicle.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 차량유형 / 차량마스터 Controller
 * Flask: /api/carclass, /api/ds-vehicle, /api/vehicle/* 대응
 * URL prefix: /vehicle-api
 */
@RestController
@RequestMapping({"/vehicle-api", "/api"})
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    /** DS_VEHICLE 목록 – Flask: GET /api/ds-vehicle */
    @GetMapping("/ds-vehicle")
    public ResponseEntity<ApiResponse<Object>> getDsVehicle() {
        List<DsVehicleResponse> list = vehicleService.getDsVehicleList();
        return ResponseEntity.ok(ApiResponse.success(Map.of("ok", true, "vehicles", list)));
    }

    /** Carclass 통합 조회 – Flask: GET /api/carclass */
    @GetMapping("/carclass")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCarclass() {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.getCarclass()));
    }

    /** 제품군별 차량 톤수 – Flask: GET /api/carclass-by-product */
    @GetMapping("/carclass-by-product")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCarclassByProduct(
            @RequestParam(required = false) String productGroup) {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.getCarclassByProduct(productGroup)));
    }

    /** Carclass/DS_VEHICLE 저장 – Flask: POST /api/carclass/save */
    @PostMapping("/carclass/save")
    public ResponseEntity<ApiResponse<Object>> saveCarclass(
            @RequestBody VehicleSaveRequest req) {
        vehicleService.saveCarclass(req);
        return ResponseEntity.ok(ApiResponse.success(Map.of("ok", true)));
    }

    /** VHCMA 차량 목록 – Flask: GET /api/vehicle/list */
    @GetMapping("/vehicle/list")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVhcmaList(
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String shipPoint,
            @RequestParam(required = false) String productGroup,
            @RequestParam(required = false) String deliveryZone,
            @RequestParam(required = false) String carrier,
            @RequestParam(required = false) String vehicleType,
            @RequestParam(required = false) String vehicleKind,
            @RequestParam(required = false) String vehicleClass,
            @RequestParam(required = false) String vehicleNo,
            @RequestParam(required = false) String sortCol,
            @RequestParam(defaultValue = "ASC") String sortDir) {

        VhcmaSearchRequest req = new VhcmaSearchRequest();
        req.setPage(page); req.setSize(size);
        req.setShipPoint(shipPoint); req.setProductGroup(productGroup);
        req.setDeliveryZone(deliveryZone); req.setCarrier(carrier);
        req.setVehicleType(vehicleType); req.setVehicleKind(vehicleKind);
        req.setVehicleClass(vehicleClass); req.setVehicleNo(vehicleNo);
        req.setSortCol(sortCol); req.setSortDir(sortDir);

        return ResponseEntity.ok(ApiResponse.success(vehicleService.getVhcmaList(req)));
    }

    /** VHCMA 차량 상세 – Flask: GET /api/vehicle/detail/<vehicle_no> */
    @GetMapping("/vehicle/detail/{vehicleNo}")
    public ResponseEntity<ApiResponse<Vhcma>> getVhcmaDetail(
            @PathVariable String vehicleNo,
            @RequestParam(defaultValue = "KN") String ownrky) {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.getVhcmaDetail(vehicleNo, ownrky)));
    }

    /** VHCMA 차량 저장 – Flask: POST /api/vehicle/save */
    @PostMapping("/vehicle/save")
    public ResponseEntity<ApiResponse<Object>> saveVhcma(@RequestBody VhcmaSaveRequest req) {
        String action = vehicleService.saveVhcma(req);
        return ResponseEntity.ok(ApiResponse.success(Map.of("ok", true, "action", action)));
    }

    /** VHCMA 차량 삭제 – Flask: POST /api/vehicle/delete */
    @PostMapping("/vehicle/delete")
    public ResponseEntity<ApiResponse<Object>> deleteVhcma(@RequestBody Map<String, String> body) {
        vehicleService.deleteVhcma(body.get("VEHICLE_NO"), body.getOrDefault("OWNRKY", "KN"));
        return ResponseEntity.ok(ApiResponse.success(Map.of("ok", true)));
    }
}
