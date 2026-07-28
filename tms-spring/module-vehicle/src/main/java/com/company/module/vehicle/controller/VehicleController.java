package com.company.module.vehicle.controller;

import com.company.core.common.response.ApiResponse;
import com.company.module.vehicle.dto.*;
import com.company.module.vehicle.entity.wms.Vhcma;
import com.company.module.vehicle.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 차량유형 / 차량마스터 Controller
 * URL prefix: /vehicle-api (+ /api 의 VHCMA 전용 엔드포인트)
 *
 * NOTE: 차종(DS_VEHICLE/CMCDV) 조회·저장 엔드포인트
 *   GET  /api/carclass, /api/carclass-by-product, /api/ds-vehicle
 *   POST /api/carclass/save
 * 는 {@link com.company.module.wms.controller.StrategyController} 에도 동일하게
 * 매핑되어 있어 Spring 기동/요청 시
 * "Ambiguous handler methods mapped for '/api/carclass/save'" 예외가 발생했다.
 * 프론트엔드(index.html)는 응답의 최상위 {@code ok}/{@code vehicles} 필드를 직접 참조하는
 * Flask 호환 형식을 기대하므로, 해당 형식을 그대로 반환하는 StrategyController 쪽을
 * 정식 핸들러로 유지하고, 본 컨트롤러의 중복 매핑(carclass/ds-vehicle 계열)은 제거한다.
 * (본 컨트롤러는 VHCMA 차량마스터 전용 기능만 담당)
 */
@RestController
@RequestMapping({"/vehicle-api", "/api"})
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

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
