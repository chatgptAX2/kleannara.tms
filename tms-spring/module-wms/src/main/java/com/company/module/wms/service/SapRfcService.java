package com.company.module.wms.service;

import com.company.module.wms.config.SapJcoConfig;
import com.company.module.wms.config.SapJcoProperties;
import com.sap.conn.jco.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * SAP JCo 직접 연결 서비스
 *
 * ■ 연결 방식
 *   JCo 직접 연결 (sapjco3.jar + libsapjco3.so)
 *   JCoDestinationManager.getDestination(DEST_NAME) → JCoFunction 실행
 *
 * ■ 호출 RFC
 *   Z_TMS_SHIPMENT_CRDL — SAP 선적 생성/삭제
 *     IMPORT: I_GUBUN (C=생성/D=삭제), I_TKNUM (삭제 시 선적번호)
 *     TABLE:  T_VBELN (납품문서 목록, 생성 시)
 *     EXPORT: E_TKNUM (생성된 선적번호), E_RETURN (결과 메시지)
 *
 * ■ Mock 모드
 *   sap.jco.mock=true 시 JCo 연결 없이 더미 응답 반환
 *
 * ■ 서버 환경 필수 조건
 *   - /data/tms/app/libs/sapjco3.jar
 *   - /data/tms/app/libs/libsapjco3.so
 *   - systemd ExecStart 에 -Djava.library.path=/data/tms/app/libs 추가
 */
@Slf4j
@Service
public class SapRfcService {

    private static final String RFC_SHIPMENT = "Z_TMS_SHIPMENT_CRDL";

    private static final DateTimeFormatter YMDFORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HMSFORMAT = DateTimeFormatter.ofPattern("HHmmss");

    // Mock 선적번호 채번용 카운터
    private static final AtomicLong MOCK_SEQ = new AtomicLong(
        System.currentTimeMillis() % 10_000_000L
    );

    /** Oracle WMS — KNRAWMS 테이블 (SHPDI/SHPDH 등, 선적 생성/삭제에 미사용) */
    private final JdbcTemplate       wmsJdbc;
    /** MariaDB TMS — PS_DISPATCH_H/D, VHCMA (배차 조회/확정) */
    private final JdbcTemplate       tmsJdbc;
    private final SapJcoProperties   jcoProps;

    public SapRfcService(
            @Qualifier("wmsJdbcTemplate") JdbcTemplate wmsJdbc,
            @Qualifier("tmsJdbcTemplate") JdbcTemplate tmsJdbc,
            SapJcoProperties jcoProps) {
        this.wmsJdbc  = wmsJdbc;
        this.tmsJdbc  = tmsJdbc;
        this.jcoProps = jcoProps;
    }

    // ════════════════════════════════════════════════════════════════
    //  선적 생성 (GUBUN='C')
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public Map<String, Object> shipmentCreate(Map<String, Object> body) {
        Long   dispHId = toLong(body.get("disp_h_id"));
        if (dispHId == null) return err("disp_h_id 필수");

        // 1) 배차 헤더 조회 (MariaDB PS_DISPATCH_H → tmsJdbc)
        List<Map<String, Object>> heads = tmsJdbc.queryForList(
            "SELECT * FROM KNRAWMS.PS_DISPATCH_H WHERE DISP_H_ID=?", dispHId
        );
        if (heads.isEmpty()) return err("배차 문서 없음: disp_h_id=" + dispHId);
        Map<String, Object> head = heads.get(0);

        String status     = str(head.get("STATUS"));
        String dispatchNo = str(head.get("DISPATCH_NO"));
        String existTknum = str(head.get("TKNUM"));

        if ("SAP_CREATED".equals(status) && !existTknum.isEmpty()) {
            return Map.of("ok", true, "message", "이미 선적 생성됨", "tknum", existTknum, "mock", false);
        }

        // 2) 납품문서 목록 수집 (MariaDB PS_DISPATCH_D → tmsJdbc)
        List<Map<String, Object>> details = tmsJdbc.queryForList(
            "SELECT DISTINCT SHPOKY FROM KNRAWMS.PS_DISPATCH_D WHERE DISP_H_ID=?", dispHId
        );
        List<String> vbelnList = details.stream()
            .map(r -> str(r.get("SHPOKY"))).filter(s -> !s.isEmpty())
            .collect(Collectors.toList());

        // 3) RFC 호출
        Map<String, Object> rfcResult = callSapRfcShipment("C", vbelnList, "");
        boolean rfcOk  = Boolean.TRUE.equals(rfcResult.get("ok"));
        String  tknum  = str(rfcResult.get("E_TKNUM"));
        boolean isMock = Boolean.TRUE.equals(rfcResult.get("mock"));

        if (!rfcOk) {
            log.warn("SAP RFC 선적생성 실패: dispHId={}, result={}", dispHId, rfcResult);
            return Map.of("ok", false,
                          "error", "SAP RFC 오류: " + rfcResult.get("E_RETURN"),
                          "mock", isMock);
        }

        // 4) DB 업데이트
        // 4) DB 업데이트 (MariaDB PS_DISPATCH_H → tmsJdbc)
        String today = LocalDate.now().format(YMDFORMAT);
        tmsJdbc.update(
            "UPDATE PS_DISPATCH_H SET STATUS='SAP_CREATED', TKNUM=?, LMODAT=? WHERE DISP_H_ID=?",
            tknum, today, dispHId
        );
        log.info("선적 생성 완료: dispHId={}, tknum={}, mock={}", dispHId, tknum, isMock);

        return Map.of(
            "ok",      true,
            "tknum",   tknum,
            "mock",    isMock,
            "message", isMock ? "[MOCK] 선적 생성 완료" : "SAP 선적 생성 완료"
        );
    }

    // ════════════════════════════════════════════════════════════════
    //  선적 삭제 (GUBUN='D')
    // ════════════════════════════════════════════════════════════════

    @Transactional
    public Map<String, Object> shipmentDelete(Map<String, Object> body) {
        Long   dispHId = toLong(body.get("disp_h_id"));
        if (dispHId == null) return err("disp_h_id 필수");

        List<Map<String, Object>> heads = tmsJdbc.queryForList(
            "SELECT * FROM KNRAWMS.PS_DISPATCH_H WHERE DISP_H_ID=?", dispHId
        );
        if (heads.isEmpty()) return err("배차 문서 없음: disp_h_id=" + dispHId);
        Map<String, Object> head = heads.get(0);

        String tknum      = str(head.get("TKNUM"));
        String dispatchNo = str(head.get("DISPATCH_NO"));

        if (tknum.isEmpty()) {
            tmsJdbc.update("UPDATE PS_DISPATCH_H SET STATUS='CONFIRMED', LMODAT=? WHERE DISP_H_ID=?",
                LocalDate.now().format(YMDFORMAT), dispHId);
            return Map.of("ok", true, "message", "TKNUM 없음 — 상태만 CONFIRMED로 복원", "mock", false);
        }

        // RFC 호출 (삭제)
        Map<String, Object> rfcResult = callSapRfcShipment("D", Collections.emptyList(), tknum);
        boolean rfcOk  = Boolean.TRUE.equals(rfcResult.get("ok"));
        boolean isMock = Boolean.TRUE.equals(rfcResult.get("mock"));

        if (!rfcOk) {
            log.warn("SAP RFC 선적삭제 실패: dispHId={}, tknum={}, result={}", dispHId, tknum, rfcResult);
            return Map.of("ok", false,
                          "error", "SAP RFC 오류: " + rfcResult.get("E_RETURN"),
                          "mock", isMock);
        }

        String today = LocalDate.now().format(YMDFORMAT);
        tmsJdbc.update(
            "UPDATE PS_DISPATCH_H SET STATUS='CONFIRMED', TKNUM=NULL, SVBELN=NULL, LMODAT=? WHERE DISP_H_ID=?",
            today, dispHId
        );
        log.info("선적 삭제 완료: dispHId={}, tknum={}, mock={}", dispHId, tknum, isMock);

        return Map.of(
            "ok",      true,
            "mock",    isMock,
            "message", isMock ? "[MOCK] 선적 삭제 완료" : "SAP 선적 삭제 완료"
        );
    }

    // ════════════════════════════════════════════════════════════════
    //  Z_TMS_SHIPMENT_CRDL JCo 직접 호출
    // ════════════════════════════════════════════════════════════════

    /**
     * SAP RFC Z_TMS_SHIPMENT_CRDL 직접 호출 (JCo)
     *
     * @param gubun     'C' = 선적 생성 / 'D' = 선적 삭제
     * @param vbelnList 선적 생성 시 납품문서 번호 목록
     * @param tknum     선적 삭제 시 SAP 선적번호
     */
    public Map<String, Object> callSapRfcShipment(String gubun,
                                                   List<String> vbelnList,
                                                   String tknum) {
        // Mock 모드
        if (jcoProps.isMock()) {
            return sapRfcMock(gubun, tknum, "mock=true");
        }

        try {
            // 1) Destination 획득 (커넥션 풀에서 연결 대여)
            JCoDestination dest = JCoDestinationManager.getDestination(SapJcoConfig.DEST_NAME);

            // 2) RFC Function 객체 생성
            JCoFunction function = dest.getRepository().getFunction(RFC_SHIPMENT);
            if (function == null) {
                return err("RFC 함수를 찾을 수 없음: " + RFC_SHIPMENT);
            }

            // 3) IMPORT 파라미터 설정
            JCoParameterList imports = function.getImportParameterList();
            imports.setValue("I_GUBUN", gubun);
            imports.setValue("I_TKNUM", tknum != null ? tknum : "");
            imports.setValue("I_VBELN", "");

            // 4) TABLE T_VBELN 설정 (선적 생성 시만)
            if ("C".equals(gubun) && vbelnList != null && !vbelnList.isEmpty()) {
                JCoTable tVbeln = function.getTableParameterList().getTable("T_VBELN");
                for (String vbeln : vbelnList) {
                    tVbeln.appendRow();
                    // 납품문서번호 10자리 zero-padding
                    tVbeln.setValue("VBELN", String.format("%010d", safeParseLong(vbeln)));
                }
            }

            // 5) RFC 실행
            function.execute(dest);

            // 6) EXPORT 파라미터 수신
            JCoParameterList exports = function.getExportParameterList();
            String eTknum = exports.getString("E_TKNUM");

            // E_RETURN 구조체 파싱
            JCoStructure eReturn  = exports.getStructure("E_RETURN");
            String retType  = eReturn.getString("TYPE");
            String retCode  = eReturn.getString("CODE");
            String retMsg   = buildReturnMessage(eReturn);

            boolean ok = "S".equals(retType) || "I".equals(retType);
            log.info("SAP RFC {} 결과: type={}, code={}, tknum={}, msg={}",
                     gubun, retType, retCode, eTknum, retMsg);

            Map<String, Object> eReturnMap = new LinkedHashMap<>();
            eReturnMap.put("TYPE",    retType);
            eReturnMap.put("CODE",    retCode);
            eReturnMap.put("MESSAGE", retMsg);

            return Map.of(
                "ok",       ok,
                "E_RETURN", eReturnMap,
                "E_TKNUM",  eTknum != null ? eTknum : "",
                "mock",     false
            );

        } catch (JCoException e) {
            log.error("SAP JCo RFC 호출 실패: gubun={}, key={}, msg={}",
                      gubun, e.getKey(), e.getMessage(), e);
            return Map.of(
                "ok",       false,
                "E_RETURN", Map.of("TYPE", "E", "CODE", e.getKey(), "MESSAGE", e.getMessage()),
                "E_TKNUM",  "",
                "mock",     false
            );
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  SAP 연결 테스트 (ping)
    // ════════════════════════════════════════════════════════════════

    /**
     * SAP 연결 상태 확인
     * GET /api/ps-sap/ping 에서 호출
     */
    public Map<String, Object> ping() {
        if (jcoProps.isMock()) {
            return Map.of("ok", true, "mock", true,
                          "message", "[MOCK] SAP 연결 테스트 생략");
        }
        try {
            JCoDestination dest = JCoDestinationManager.getDestination(SapJcoConfig.DEST_NAME);
            dest.ping();
            log.info("[SAP JCo] ping 성공: ashost={}", jcoProps.getAshost());
            return Map.of("ok", true, "mock", false,
                          "message", "SAP 연결 정상",
                          "ashost",  jcoProps.getAshost(),
                          "sysnum",  jcoProps.getSysnum(),
                          "client",  jcoProps.getClient());
        } catch (JCoException e) {
            log.error("[SAP JCo] ping 실패: {}", e.getMessage(), e);
            return Map.of("ok", false, "mock", false,
                          "error",   e.getMessage(),
                          "key",     e.getKey());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  조회 API (DB 기반 — JCo 미사용)
    // ════════════════════════════════════════════════════════════════

    public Map<String, Object> sapList(Map<String, Object> body) {
        try {
            String dateFrom = str(body.get("dateFrom")).replace("-", "");
            String dateTo   = str(body.get("dateTo")).replace("-", "");
            String dptnky   = str(body.get("dptnky"));

            // PS_DISPATCH_H / PS_DISPATCH_D → MariaDB tmsJdbc
            StringBuilder sql = new StringBuilder(
                "SELECT h.DISP_H_ID, h.DISPATCH_NO, h.DPTNKY, h.DPTNM, h.DISP_DATE, " +
                "       h.STATUS, h.CARTYPE, h.DRIVER_NM, h.DRIVER_TEL, h.TKNUM, h.SVBELN, " +
                "       COUNT(d.DISP_D_ID) AS ITEM_CNT " +
                "FROM KNRAWMS.PS_DISPATCH_H h LEFT JOIN KNRAWMS.PS_DISPATCH_D d ON d.DISP_H_ID=h.DISP_H_ID " +
                "WHERE h.STATUS IN ('CONFIRMED','SAP_CREATED') "
            );
            List<Object> args = new ArrayList<>();
            if (!dateFrom.isEmpty()) { sql.append("AND h.DISP_DATE>=? "); args.add(dateFrom); }
            if (!dateTo.isEmpty())   { sql.append("AND h.DISP_DATE<=? "); args.add(dateTo); }
            if (!dptnky.isEmpty())   { sql.append("AND h.DPTNKY=? ");     args.add(dptnky); }
            sql.append("GROUP BY h.DISP_H_ID ORDER BY h.DISP_DATE DESC, h.DISPATCH_NO");

            List<Map<String, Object>> rows = tmsJdbc.queryForList(sql.toString(), args.toArray());
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) { return errMap(e); }
    }

    public Map<String, Object> sapItems(Map<String, Object> body) {
        Long dispHId = toLong(body.get("disp_h_id"));
        if (dispHId == null) return err("disp_h_id 필수");
        try {
            // PS_DISPATCH_D / PS_DISPATCH_H → MariaDB tmsJdbc
            List<Map<String, Object>> rows = tmsJdbc.queryForList(
                "SELECT d.*, h.CARTYPE, h.DISP_DATE FROM KNRAWMS.PS_DISPATCH_D d " +
                "JOIN KNRAWMS.PS_DISPATCH_H h ON h.DISP_H_ID=d.DISP_H_ID " +
                "WHERE d.DISP_H_ID=? ORDER BY d.ITEM_SEQ", dispHId
            );
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) { return errMap(e); }
    }

    public Map<String, Object> sapDocs(Map<String, Object> body) {
        Long dispHId = toLong(body.get("disp_h_id"));
        if (dispHId == null) return err("disp_h_id 필수");
        try {
            // DOC_FILE → MariaDB tmsJdbc
            List<Map<String, Object>> rows = tmsJdbc.queryForList(
                "SELECT * FROM KNRAWMS.DOC_FILE WHERE DEL_YN='N' " +
                "AND FILE_NM LIKE CONCAT('%',?,'%') ORDER BY CREDAT DESC, FILE_ID DESC",
                dispHId.toString()
            );
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) { return errMap(e); }
    }

    public Map<String, Object> vehicleSearch(Map<String, Object> body) {
        try {
            // VHCMA → MariaDB tmsJdbc
            String cartype = str(body.get("cartype"));
            String sql = "SELECT * FROM KNRAWMS.VHCMA WHERE " +
                (cartype.isEmpty() ? "1=1" : "CARTYPE=?") +
                " AND (USE_YN IS NULL OR USE_YN='Y') ORDER BY VHCLNO FETCH FIRST 100 ROWS ONLY";
            List<Map<String, Object>> rows = cartype.isEmpty()
                ? tmsJdbc.queryForList(sql)
                : tmsJdbc.queryForList(sql, cartype);
            return Map.of("ok", true, "vehicles", rows);
        } catch (Exception e) { return errMap(e); }
    }

    @Transactional
    public Map<String, Object> assignVehicle(Map<String, Object> body) {
        Long dispHId = toLong(body.get("disp_h_id"));
        if (dispHId == null) return err("disp_h_id 필수");
        try {
            // PS_DISPATCH_H → MariaDB tmsJdbc
            tmsJdbc.update(
                "UPDATE PS_DISPATCH_H SET VHCLNO=?, DRIVER_NM=?, DRIVER_TEL=?, LMODAT=? WHERE DISP_H_ID=?",
                str(body.get("vhclno")), str(body.get("driver_nm")), str(body.get("driver_tel")),
                LocalDate.now().format(YMDFORMAT), dispHId
            );
            return Map.of("ok", true);
        } catch (Exception e) { return errMap(e); }
    }

    // ════════════════════════════════════════════════════════════════
    //  Mock 응답
    // ════════════════════════════════════════════════════════════════

    private Map<String, Object> sapRfcMock(String gubun, String tknum, String reason) {
        if ("C".equals(gubun)) {
            long seq = MOCK_SEQ.incrementAndGet() % 10_000_000L;
            String mockTknum = String.format("9%07d", seq);
            log.info("[MOCK] SAP RFC 선적생성: tknum={}, reason={}", mockTknum, reason);
            return Map.of(
                "ok",       true,
                "E_RETURN", Map.of("TYPE","S","CODE","488",
                                   "MESSAGE","[MOCK] 선적문서 생성됨 [" + mockTknum + "]"),
                "E_TKNUM",  mockTknum,
                "mock",     true
            );
        } else {
            log.info("[MOCK] SAP RFC 선적삭제: tknum={}, reason={}", tknum, reason);
            return Map.of(
                "ok",       true,
                "E_RETURN", Map.of("TYPE","S","CODE","489","MESSAGE","[MOCK] 선적문서 삭제됨"),
                "E_TKNUM",  "",
                "mock",     true
            );
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  헬퍼
    // ════════════════════════════════════════════════════════════════

    private String buildReturnMessage(JCoStructure eReturn) {
        StringBuilder sb = new StringBuilder(nullSafe(eReturn.getString("MESSAGE")));
        for (String vi : List.of("MESSAGE_V1","MESSAGE_V2","MESSAGE_V3","MESSAGE_V4")) {
            try {
                String v = eReturn.getString(vi);
                if (v != null && !v.isBlank()) sb.append(" ").append(v);
            } catch (JCoRuntimeException ignored) { /* 필드 없으면 무시 */ }
        }
        return sb.toString().trim();
    }

    private long safeParseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0L; }
    }

    private Map<String, Object> err(String msg) {
        return Map.of("ok", false, "error", msg);
    }

    private Map<String, Object> errMap(Exception e) {
        log.error("SapRfcService error: {}", e.getMessage(), e);
        return Map.of("ok", false, "error", e.getMessage() != null ? e.getMessage() : "알 수 없는 오류");
    }

    private String str(Object v)      { return v == null ? "" : v.toString().trim(); }
    private String nullSafe(String v) { return v == null ? "" : v; }

    private Long toLong(Object v) {
        try { return v == null ? null : Long.valueOf(v.toString()); }
        catch (Exception e) { return null; }
    }
}
