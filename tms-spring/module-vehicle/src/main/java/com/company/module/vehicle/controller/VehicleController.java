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

    /**
     * VHCMA 차량 목록 – Flask: GET /api/vehicle/list
     *
     * NOTE(bind 이슈 수정): 프론트엔드(index.html)는 Flask 호환 형식으로 응답 최상위의
     *   {@code total}/{@code rows}/{@code ship_points} 필드를 직접 참조한다.
     *   (예: searchVehicle() → data.total / data.rows, /api/codes 도 List 직접 반환)
     *   기존처럼 {@code ApiResponse.success()} 로 한 번 더 감싸면
     *   실제 응답이 {@code {success,code,data:{total,rows,...}}} 가 되어
     *   프론트의 data.rows/data.total 이 undefined → 화면에 결과가 바인딩되지 않았다.
     *   → 목록 응답 Map 을 그대로(unwrap) 반환하도록 변경한다.
     */
    @GetMapping("/vehicle/list")
    public ResponseEntity<Map<String, Object>> getVhcmaList(
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "50") int size,
            // 프론트는 snake_case 파라미터명(ship_point, vehicle_type 등)으로 전송하므로
            //   @RequestParam(name=...) 로 명시 매핑해야 값이 바인딩됨.
            //   (미지정 시 camelCase 기대 → vehicle_type/vehicle_class/vehicle_no 등이 null 로 유실되어 필터 미적용)
            @RequestParam(name = "ship_point",    required = false) String shipPoint,
            @RequestParam(name = "product_group", required = false) String productGroup,
            @RequestParam(name = "delivery_zone", required = false) String deliveryZone,
            @RequestParam(name = "carrier",       required = false) String carrier,
            @RequestParam(name = "vehicle_type",  required = false) String vehicleType,
            @RequestParam(name = "vehicle_kind",  required = false) String vehicleKind,
            @RequestParam(name = "vehicle_class", required = false) String vehicleClass,
            @RequestParam(name = "vehicle_no",    required = false) String vehicleNo,
            @RequestParam(name = "use_yn",        required = false) String useYn,
            @RequestParam(name = "sort_col",      required = false) String sortCol,
            @RequestParam(name = "sort_dir", defaultValue = "ASC")  String sortDir) {

        VhcmaSearchRequest req = new VhcmaSearchRequest();
        req.setPage(page); req.setSize(size);
        req.setShipPoint(shipPoint); req.setProductGroup(productGroup);
        req.setDeliveryZone(deliveryZone);
        // 운송사: 프론트가 정규화 코드(0000300342)로 전송 → DB CARRIER 는 앞 '0000' 제거된
        //   코드(300342)로 저장되어 있으므로, 선행 '0000' 을 제거한 값으로 LIKE 검색 수행.
        req.setCarrier(stripCarrierPrefix(carrier));
        req.setVehicleType(vehicleType); req.setVehicleKind(vehicleKind);
        req.setVehicleClass(vehicleClass); req.setVehicleNo(vehicleNo);
        req.setUseYn(useYn);
        req.setSortCol(sortCol); req.setSortDir(sortDir);

        return ResponseEntity.ok(vehicleService.getVhcmaList(req));
    }

    /** 운송사 파라미터 정규화 — 프론트 전송값(예 0000300342)에서 선행 '0000' 제거 후 검색.
     *  - '0000' 접두어가 있을 때만 제거(그 외 값은 원본 유지).
     *  - 결과가 비면 null 반환(조건 미적용). */
    private String stripCarrierPrefix(String carrier) {
        if (carrier == null) return null;
        String v = carrier.strip();
        if (v.isEmpty()) return null;
        if (v.startsWith("0000")) v = v.substring(4);
        return v.isEmpty() ? null : v;
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
