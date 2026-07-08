package com.company.module.wms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * SAP RFC / WMS IFC301 HTTP 실제 연동 서비스 — Flask _call_sap_rfc_shipment() + _call_wms_ifc301() 완전 Java 포팅
 *
 * ■ Z_TMS_SHIPMENT_CRDL RFC 호출 (SAP 선적생성/삭제)
 *   - POST {sap.rfc.url}/Z_TMS_SHIPMENT_CRDL
 *   - Body: { I_GUBUN, I_TKNUM, T_VBELN: [{VBELN},...] }
 *   - Response: { E_RETURN: {TYPE, CODE, MESSAGE}, E_TKNUM }
 *
 * ■ WMS_IFC301 공통처리 호출 (WMS 연동)
 *   - POST {sap.wms.url}
 *   - Body: { GUBUN, STDLNR, TKNUM }
 *
 * ■ Mock 모드: sap.rfc.mock=true 설정 시 SAP 연결 없이 더미 응답 반환
 */
@Slf4j
@Service
public class SapRfcService {

    private final JdbcTemplate   jdbc;
    private final RestTemplate   restTemplate;

    @Value("${sap.rfc.url:http://localhost:8000/sap/rfc}")
    private String sapRfcUrl;

    @Value("${sap.wms.url:http://localhost:9000/wms/ifc}")
    private String sapWmsUrl;

    /** true 설정 시 SAP 연결 없이 mock 응답 반환 (개발/테스트 환경) */
    @Value("${sap.rfc.mock:false}")
    private boolean mockMode;

    /** RFC 호출 타임아웃 (초, 기본 30초) */
    @Value("${sap.rfc.timeout-seconds:30}")
    private int rfcTimeoutSec;

    // 배차번호 자동 채번용 Mock 카운터
    private static final AtomicLong MOCK_SEQ = new AtomicLong(
        System.currentTimeMillis() % 10_000_000L
    );

    private static final DateTimeFormatter YMDFORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HMSFORMAT = DateTimeFormatter.ofPattern("HHmmss");

    public SapRfcService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        // RestTemplate 직접 생성 (Spring Boot 3 기준 — @Bean 불필요)
        this.restTemplate = new RestTemplate();
    }

    // ════════════════════════════════════════════════════════════════
    //  선적 생성 (GUBUN='C')
    // ════════════════════════════════════════════════════════════════

    /**
     * SAP 선적 생성 전체 흐름:
     *   1) PS_DISPATCH_H / PS_DISPATCH_D 조회
     *   2) Z_TMS_SHIPMENT_CRDL RFC 호출 (GUBUN='C', T_VBELN=납품문서 목록)
     *   3) E_TKNUM 반환 성공 시 → PS_DISPATCH_H.TKNUM 업데이트, STATUS='SAP_CREATED'
     *   4) WMS_IFC301 호출 (GUBUN='C', STDLNR=DISPATCH_NO, TKNUM=E_TKNUM)
     */
    @Transactional
    public Map<String, Object> shipmentCreate(Map<String, Object> body) {
        Long   dispHId = toLong(body.get("disp_h_id"));
        String env     = str(body.getOrDefault("env", "prod")).toLowerCase();
        if (dispHId == null) return err("disp_h_id 필수");

        // 1) 배차 헤더 조회
        List<Map<String, Object>> heads = jdbc.queryForList(
            "SELECT * FROM PS_DISPATCH_H WHERE DISP_H_ID=?", dispHId
        );
        if (heads.isEmpty()) return err("배차 문서 없음: disp_h_id=" + dispHId);
        Map<String, Object> head = heads.get(0);

        String status     = str(head.get("STATUS"));
        String dispatchNo = str(head.get("DISPATCH_NO"));
        String existTknum = str(head.get("TKNUM"));

        // 이미 SAP_CREATED 상태이고 TKNUM 있으면 중복 방지
        if ("SAP_CREATED".equals(status) && !existTknum.isEmpty()) {
            return Map.of("ok", true, "message", "이미 선적 생성됨", "tknum", existTknum, "mock", false);
        }

        // 2) 납품문서 목록 수집 (PS_DISPATCH_D.SHPOKY)
        List<Map<String, Object>> details = jdbc.queryForList(
            "SELECT DISTINCT SHPOKY FROM PS_DISPATCH_D WHERE DISP_H_ID=?", dispHId
        );
        List<String> svbelnList = details.stream()
            .map(r -> str(r.get("SHPOKY"))).filter(s -> !s.isEmpty())
            .collect(Collectors.toList());

        // 3) RFC 호출
        Map<String, Object> rfcResult = callSapRfcShipment("C", svbelnList, "", env);
        boolean rfcOk = Boolean.TRUE.equals(rfcResult.get("ok"));
        String  tknum = str(rfcResult.get("E_TKNUM"));
        boolean isMock = Boolean.TRUE.equals(rfcResult.get("mock"));

        if (!rfcOk) {
            log.warn("SAP RFC 선적생성 실패: dispHId={}, result={}", dispHId, rfcResult);
            return Map.of("ok", false, "error", "SAP RFC 오류: " + rfcResult.get("E_RETURN"),
                          "mock", isMock);
        }

        String today = LocalDate.now().format(YMDFORMAT);

        // 4) DB 업데이트
        jdbc.update(
            "UPDATE PS_DISPATCH_H SET STATUS='SAP_CREATED', TKNUM=?, LMODAT=? WHERE DISP_H_ID=?",
            tknum, today, dispHId
        );
        log.info("선적 생성 완료: dispHId={}, tknum={}, mock={}", dispHId, tknum, isMock);

        // 5) WMS IFC301 호출
        Map<String, Object> wmsResult = callWmsIfc301(dispatchNo, tknum, "C", env);
        if (!Boolean.TRUE.equals(wmsResult.get("ok"))) {
            log.warn("WMS IFC301 선적생성 통지 실패 (DB는 업데이트 완료): {}", wmsResult);
        }

        return Map.of(
            "ok",      true,
            "tknum",   tknum,
            "mock",    isMock,
            "wms",     wmsResult,
            "message", isMock ? "[MOCK] 선적 생성 완료" : "SAP 선적 생성 완료"
        );
    }

    // ════════════════════════════════════════════════════════════════
    //  선적 삭제 (GUBUN='D')
    // ════════════════════════════════════════════════════════════════

    /**
     * SAP 선적 삭제 전체 흐름:
     *   1) PS_DISPATCH_H 조회 → TKNUM 확인
     *   2) Z_TMS_SHIPMENT_CRDL RFC 호출 (GUBUN='D', I_TKNUM=TKNUM)
     *   3) 성공 시 → STATUS='CONFIRMED', TKNUM/SVBELN NULL 클리어
     *   4) WMS_IFC301 호출 (GUBUN='D')
     */
    @Transactional
    public Map<String, Object> shipmentDelete(Map<String, Object> body) {
        Long   dispHId = toLong(body.get("disp_h_id"));
        String env     = str(body.getOrDefault("env", "prod")).toLowerCase();
        if (dispHId == null) return err("disp_h_id 필수");

        List<Map<String, Object>> heads = jdbc.queryForList(
            "SELECT * FROM PS_DISPATCH_H WHERE DISP_H_ID=?", dispHId
        );
        if (heads.isEmpty()) return err("배차 문서 없음: disp_h_id=" + dispHId);
        Map<String, Object> head = heads.get(0);

        String tknum      = str(head.get("TKNUM"));
        String svbeln     = str(head.get("SVBELN"));
        String dispatchNo = str(head.get("DISPATCH_NO"));

        // TKNUM 없으면 상태만 롤백
        if (tknum.isEmpty()) {
            jdbc.update("UPDATE PS_DISPATCH_H SET STATUS='CONFIRMED', LMODAT=? WHERE DISP_H_ID=?",
                LocalDate.now().format(YMDFORMAT), dispHId);
            return Map.of("ok", true, "message", "TKNUM 없음 — 상태만 CONFIRMED로 복원", "mock", false);
        }

        // RFC 호출 (삭제)
        Map<String, Object> rfcResult = callSapRfcShipment("D", Collections.emptyList(), tknum, env);
        boolean rfcOk  = Boolean.TRUE.equals(rfcResult.get("ok"));
        boolean isMock = Boolean.TRUE.equals(rfcResult.get("mock"));

        if (!rfcOk) {
            log.warn("SAP RFC 선적삭제 실패: dispHId={}, tknum={}, result={}", dispHId, tknum, rfcResult);
            return Map.of("ok", false, "error", "SAP RFC 오류: " + rfcResult.get("E_RETURN"),
                          "mock", isMock);
        }

        String today = LocalDate.now().format(YMDFORMAT);
        jdbc.update(
            "UPDATE PS_DISPATCH_H SET STATUS='CONFIRMED', TKNUM=NULL, SVBELN=NULL, LMODAT=? WHERE DISP_H_ID=?",
            today, dispHId
        );
        log.info("선적 삭제 완료: dispHId={}, tknum={}, mock={}", dispHId, tknum, isMock);

        // WMS IFC301 삭제 통지
        Map<String, Object> wmsResult = callWmsIfc301(dispatchNo, tknum, "D", env);
        if (!Boolean.TRUE.equals(wmsResult.get("ok"))) {
            log.warn("WMS IFC301 선적삭제 통지 실패 (DB는 업데이트 완료): {}", wmsResult);
        }

        return Map.of(
            "ok",      true,
            "mock",    isMock,
            "wms",     wmsResult,
            "message", isMock ? "[MOCK] 선적 삭제 완료" : "SAP 선적 삭제 완료"
        );
    }

    // ════════════════════════════════════════════════════════════════
    //  Z_TMS_SHIPMENT_CRDL RFC HTTP 호출
    // ════════════════════════════════════════════════════════════════

    /**
     * Z_TMS_SHIPMENT_CRDL RFC 직접 호출.
     *
     * POST {sapRfcUrl}/Z_TMS_SHIPMENT_CRDL
     * Request body:
     * {
     *   "I_GUBUN": "C" | "D",
     *   "I_TKNUM": "선적번호(삭제 시)",
     *   "I_VBELN": "",
     *   "T_VBELN": [{"VBELN": "납품문서번호"}, ...]
     * }
     * Response:
     * {
     *   "E_RETURN": {"TYPE":"S","CODE":"488","MESSAGE":"..."},
     *   "E_TKNUM": "생성된 SAP 선적번호"
     * }
     *
     * @param gubun       'C'=선적생성 / 'D'=선적삭제
     * @param svbelnList  선적생성 시 납품문서 목록
     * @param tknum       선적삭제 시 SAP 선적번호
     * @param env         'dev' | 'prod'
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> callSapRfcShipment(String gubun, List<String> svbelnList,
                                                    String tknum, String env) {
        if (mockMode) return sapRfcMock(gubun, tknum, "mock_mode=true");

        // T_VBELN 구성
        List<Map<String, String>> tVbeln = svbelnList.stream()
            .map(v -> Map.of("VBELN", String.format("%010d", safeParseLong(v))))
            .collect(Collectors.toList());

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("I_GUBUN", gubun);
        requestBody.put("I_TKNUM", tknum != null ? tknum : "");
        requestBody.put("I_VBELN", "");
        requestBody.put("T_VBELN", tVbeln);

        String url = sapRfcUrl + "/Z_TMS_SHIPMENT_CRDL";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                return sapRfcMock(gubun, tknum, "http_status=" + response.getStatusCode());
            }

            Map<String, Object> body = response.getBody();
            if (body == null) return sapRfcMock(gubun, tknum, "empty_response");

            Map<String, Object> eReturn  = (Map<String, Object>) body.getOrDefault("E_RETURN", new HashMap<>());
            String eTknum   = str(body.get("E_TKNUM"));
            String retType  = str(eReturn.get("TYPE"));
            String retMsg   = buildReturnMessage(eReturn);

            boolean ok = "S".equals(retType);
            log.info("SAP RFC {} 결과: type={}, tknum={}, msg={}", gubun, retType, eTknum, retMsg);

            return Map.of(
                "ok",       ok,
                "E_RETURN", eReturn,
                "E_TKNUM",  eTknum,
                "mock",     false
            );

        } catch (RestClientException e) {
            String errStr = e.getMessage() != null ? e.getMessage() : "";
            boolean isConnErr = isConnectionError(errStr);
            log.warn("SAP RFC HTTP 오류 ({}): {}", gubun, errStr);
            if (isConnErr) return sapRfcMock(gubun, tknum, "conn_error: " + errStr.substring(0, Math.min(120, errStr.length())));
            return Map.of(
                "ok",       false,
                "E_RETURN", Map.of("TYPE", "E", "CODE", "", "MESSAGE", errStr.substring(0, Math.min(300, errStr.length()))),
                "E_TKNUM",  "",
                "mock",     false
            );
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  WMS IFC301 호출
    // ════════════════════════════════════════════════════════════════

    /**
     * 선적생성/삭제 후 WMS_IFC301 공통처리 API 호출.
     *
     * POST {sap.wms.url}
     * Body: { "GUBUN": "C"|"D", "STDLNR": "가선적번호", "TKNUM": "SAP선적번호" }
     *
     * @param stdlnr  가선적번호 (= DISPATCH_NO)
     * @param tknum   SAP 선적번호
     * @param gubun   'C'=생성 / 'D'=삭제
     * @param env     'dev' | 'prod' (현재 동일 URL 사용, 향후 분리 가능)
     */
    public Map<String, Object> callWmsIfc301(String stdlnr, String tknum, String gubun, String env) {
        if (mockMode) {
            return Map.of("ok", true, "mock", true, "message", "[MOCK] WMS IFC301 호출 생략");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("GUBUN",  gubun);
        payload.put("STDLNR", stdlnr);
        payload.put("TKNUM",  tknum);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                sapWmsUrl,
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                String.class
            );

            boolean ok = response.getStatusCode().is2xxSuccessful();
            String body = response.getBody() != null
                ? response.getBody().substring(0, Math.min(500, response.getBody().length()))
                : "";
            log.info("WMS IFC301 {} 결과: status={}", gubun, response.getStatusCode());
            return Map.of("ok", ok, "status_code", response.getStatusCode().value(), "body", body, "mock", false);

        } catch (RestClientException e) {
            String errStr = e.getMessage() != null ? e.getMessage() : "unknown";
            log.warn("WMS IFC301 HTTP 오류 ({}): {}", gubun, errStr);
            return Map.of("ok", false, "error", errStr, "mock", false);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Mock 응답 (SAP 연결 불가 시 개발/테스트용)
    // ════════════════════════════════════════════════════════════════

    private Map<String, Object> sapRfcMock(String gubun, String tknum, String reason) {
        if ("C".equals(gubun)) {
            // 선적생성 mock: 현재 시각 기반 더미 TKNUM
            long seq = MOCK_SEQ.incrementAndGet() % 10_000_000L;
            String mockTknum = String.format("9%07d", seq);
            log.info("[MOCK] SAP RFC 선적생성 mock: tknum={}, reason={}", mockTknum, reason);
            return Map.of(
                "ok",       true,
                "E_RETURN", Map.of("TYPE","S","CODE","488","MESSAGE","[MOCK] 선적문서 생성됨 [" + mockTknum + "] (" + reason + ")"),
                "E_TKNUM",  mockTknum,
                "mock",     true
            );
        } else {
            // 선적삭제 mock
            log.info("[MOCK] SAP RFC 선적삭제 mock: tknum={}, reason={}", tknum, reason);
            return Map.of(
                "ok",       true,
                "E_RETURN", Map.of("TYPE","S","CODE","489","MESSAGE","[MOCK] 선적문서 삭제됨 (" + reason + ")"),
                "E_TKNUM",  "",
                "mock",     true
            );
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  추가 SAP 조회 API (Flask ps-sap 포팅)
    // ════════════════════════════════════════════════════════════════

    /**
     * SAP 선적 목록 조회 (PS_DISPATCH_H 기반)
     */
    public Map<String, Object> sapList(Map<String, Object> body) {
        try {
            String dateFrom = str(body.get("dateFrom")).replace("-", "");
            String dateTo   = str(body.get("dateTo")).replace("-", "");
            String dptnky   = str(body.get("dptnky"));

            StringBuilder sql = new StringBuilder(
                "SELECT h.DISP_H_ID, h.DISPATCH_NO, h.DPTNKY, h.DPTNM, h.DISP_DATE, " +
                "       h.STATUS, h.CARTYPE, h.DRIVER_NM, h.DRIVER_TEL, h.TKNUM, h.SVBELN, " +
                "       COUNT(d.DISP_D_ID) AS ITEM_CNT " +
                "FROM PS_DISPATCH_H h LEFT JOIN PS_DISPATCH_D d ON d.DISP_H_ID=h.DISP_H_ID " +
                "WHERE h.STATUS IN ('CONFIRMED','SAP_CREATED') "
            );
            List<Object> args = new ArrayList<>();
            if (!dateFrom.isEmpty()) { sql.append("AND h.DISP_DATE>=? "); args.add(dateFrom); }
            if (!dateTo.isEmpty())   { sql.append("AND h.DISP_DATE<=? "); args.add(dateTo); }
            if (!dptnky.isEmpty())   { sql.append("AND h.DPTNKY=? "); args.add(dptnky); }
            sql.append("GROUP BY h.DISP_H_ID ORDER BY h.DISP_DATE DESC, h.DISPATCH_NO");

            List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) { return errMap(e); }
    }

    /**
     * SAP 선적 아이템 조회
     */
    public Map<String, Object> sapItems(Map<String, Object> body) {
        Long dispHId = toLong(body.get("disp_h_id"));
        if (dispHId == null) return err("disp_h_id 필수");
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT d.*, h.CARTYPE, h.DISP_DATE FROM PS_DISPATCH_D d " +
                "JOIN PS_DISPATCH_H h ON h.DISP_H_ID=d.DISP_H_ID " +
                "WHERE d.DISP_H_ID=? ORDER BY d.ITEM_SEQ", dispHId
            );
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) { return errMap(e); }
    }

    /**
     * SAP 서류 목록 (DOC_FILE 연동)
     */
    public Map<String, Object> sapDocs(Map<String, Object> body) {
        Long dispHId = toLong(body.get("disp_h_id"));
        if (dispHId == null) return err("disp_h_id 필수");
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM DOC_FILE WHERE DEL_YN='N' " +
                "AND FILE_NM LIKE CONCAT('%',?,'%') ORDER BY CREDAT DESC, FILE_ID DESC",
                dispHId.toString()
            );
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) { return errMap(e); }
    }

    /**
     * 차량 검색 (VHCMA)
     */
    public Map<String, Object> vehicleSearch(Map<String, Object> body) {
        try {
            String cartype = str(body.get("cartype"));
            String sql = "SELECT * FROM VHCMA WHERE " +
                (cartype.isEmpty() ? "1=1" : "CARTYPE=?") +
                " AND (USE_YN IS NULL OR USE_YN='Y') ORDER BY VHCLNO LIMIT 100";
            List<Map<String, Object>> rows = cartype.isEmpty()
                ? jdbc.queryForList(sql)
                : jdbc.queryForList(sql, cartype);
            return Map.of("ok", true, "vehicles", rows);
        } catch (Exception e) { return errMap(e); }
    }

    /**
     * 차량 배정
     */
    @Transactional
    public Map<String, Object> assignVehicle(Map<String, Object> body) {
        Long dispHId = toLong(body.get("disp_h_id"));
        if (dispHId == null) return err("disp_h_id 필수");
        try {
            String today = LocalDate.now().format(YMDFORMAT);
            jdbc.update(
                "UPDATE PS_DISPATCH_H SET VHCLNO=?, DRIVER_NM=?, DRIVER_TEL=?, LMODAT=? WHERE DISP_H_ID=?",
                str(body.get("vhclno")), str(body.get("driver_nm")), str(body.get("driver_tel")),
                today, dispHId
            );
            return Map.of("ok", true);
        } catch (Exception e) { return errMap(e); }
    }

    // ════════════════════════════════════════════════════════════════
    //  헬퍼
    // ════════════════════════════════════════════════════════════════

    private String buildReturnMessage(Map<String, Object> eReturn) {
        StringBuilder sb = new StringBuilder(str(eReturn.get("MESSAGE")));
        for (String vi : Arrays.asList("MESSAGE_V1","MESSAGE_V2","MESSAGE_V3","MESSAGE_V4")) {
            String v = str(eReturn.get(vi));
            if (!v.isEmpty()) sb.append(" ").append(v);
        }
        return sb.toString().trim();
    }

    private boolean isConnectionError(String msg) {
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("connection") || lower.contains("timeout")
            || lower.contains("unreachable") || lower.contains("refused")
            || lower.contains("network") || lower.contains("i/o error");
    }

    private long safeParseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0L; }
    }

    private Map<String, Object> err(String msg) {
        return Map.of("ok", false, "error", msg);
    }

    private Map<String, Object> errMap(Exception e) {
        log.error("SapRfcService error: {}", e.getMessage(), e);
        return Map.of("ok", false, "error", e.getMessage());
    }

    private String str(Object v) { return v == null ? "" : v.toString().trim(); }

    private Long toLong(Object v) {
        try { return v == null ? null : Long.valueOf(v.toString()); }
        catch (Exception e) { return null; }
    }
}
