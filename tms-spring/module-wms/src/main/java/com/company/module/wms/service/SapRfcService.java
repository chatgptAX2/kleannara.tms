package com.company.module.wms.service;

import com.company.module.wms.config.SapJcoConfig;
import com.company.module.wms.config.SapJcoProperties;
import com.sap.conn.jco.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
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
    private static final DateTimeFormatter LOG_TS   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * RFC / WMS API 호출·에러 로그를 기록할 파일.
     * -Dtms.stdout.log=/path/STDOUT.LOG 로 지정 가능. 미지정 시 작업 디렉터리 기준.
     */
    private static final String STDOUT_LOG_PATH =
        System.getProperty("tms.stdout.log",
            System.getenv().getOrDefault("TMS_STDOUT_LOG", "STDOUT.LOG"));

    /** WMS 공통처리 API (선적생성/삭제 후 WMS 동기화) — 운영 */
    private static final String WMS_IFC_URL_PROD =
        "https://wms.kleannara.com/common/tmsApi/json/WMS_IFC301.data";
    /** WMS 공통처리 API — 개발 */
    private static final String WMS_IFC_URL_DEV  =
        "https://wmsdev.kleannara.com/common/tmsApi/json/WMS_IFC301.data";

    /**
     * WMS_IFC301 호출용 HTTP 클라이언트 (JDK 내장).
     * ※ WMS 서버(https, 사내 IP/자체서명 인증서)와 통신하므로
     *   Flask 의 requests(verify=False) 와 동일하게 인증서 검증을 생략한 SSLContext 를 사용한다.
     *   (사내 폐쇄망 전용 인터페이스 — 외부 노출 없음)
     */
    private static final HttpClient HTTP = buildInsecureHttpClient();

    /** 자체서명 인증서 허용(verify=False 상당) HTTP 클라이언트 생성 */
    private static HttpClient buildInsecureHttpClient() {
        try {
            javax.net.ssl.TrustManager[] trustAll = new javax.net.ssl.TrustManager[] {
                new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) { }
                    public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) { }
                }
            };
            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            // 호스트네임 검증도 완화 (사내 IP 접속 대응)
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
            return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .sslContext(sc)
                .build();
        } catch (Exception e) {
            // SSLContext 구성 실패 시 기본 클라이언트로 폴백
            return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        }
    }

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

    /**
     * SAP 선적생성 (I_GUBUN='C').
     * Flask api_ps_sap_shipment_create 이식.
     *
     * body : { stknums: [STDLNR(=DISPATCH_NO), ...] }  ← 가선적번호 목록(1건 이상)
     *   가선적번호(STDLNR) 단위로:
     *     1) SHPDI 에서 SAP납품문서(SVBELN) 목록 조회 → T_VBELN
     *     2) RFC Z_TMS_SHIPMENT_CRDL(I_GUBUN='C') 호출
     *     3) RFC 성공 시 WMS_IFC301 공통처리 API 호출
     *     4) PS_DISPATCH_H.STKNUM = E_TKNUM (SAP선적번호) 기록
     *
     * 반환 : { ok, results:[{stdlnr, ok, tknum, mock, message, svbeln_cnt, wms_result, db_update_err, env}], env }
     */
    @Transactional
    public Map<String, Object> shipmentCreate(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> stknumsRaw = (List<Object>) body.get("stknums");
        if (stknumsRaw == null || stknumsRaw.isEmpty()) return err("stknums 필수");

        String env   = detectEnv();
        String today = LocalDate.now().format(YMDFORMAT);
        List<Map<String, Object>> results = new ArrayList<>();

        for (Object o : stknumsRaw) {
            String stdlnr = str(o);
            if (stdlnr.isEmpty()) continue;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stdlnr", stdlnr);
            row.put("env", env);

            try {
                // 1) 해당 가선적번호의 SAP납품문서(SVBELN) 목록 조회 (Oracle KNRAWMS → wmsJdbc)
                List<Map<String, Object>> svbelnRows = wmsJdbc.queryForList(
                    "SELECT DISTINCT SI.SVBELN FROM KNRAWMS.SHPDI SI " +
                    "WHERE SI.STATIT='NEW' AND SI.STDLNR=? " +
                    "AND SI.SVBELN <> ' ' ORDER BY SI.SVBELN",
                    stdlnr
                );
                List<String> vbelnList = svbelnRows.stream()
                    .map(r -> str(r.get("SVBELN"))).filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

                if (vbelnList.isEmpty()) {
                    stdoutLog("[shipment-create][SKIP] stdlnr=" + stdlnr + " SVBELN 없음");
                    row.put("ok", false);
                    row.put("message", "SAP납품문서(SVBELN)가 없습니다.");
                    results.add(row);
                    continue;
                }

                // 2) RFC 호출 (I_GUBUN='C', T_VBELN=납품문서목록)
                stdoutLog("[shipment-create][RFC-REQ] stdlnr=" + stdlnr
                        + " gubun=C vbeln=" + vbelnList);
                Map<String, Object> rfc = callSapRfcShipment("C", vbelnList, "");
                boolean rfcOk  = Boolean.TRUE.equals(rfc.get("ok"));
                String  tknum  = str(rfc.get("E_TKNUM"));
                boolean isMock = Boolean.TRUE.equals(rfc.get("mock"));
                String  msg    = rfcMessage(rfc);
                stdoutLog("[shipment-create][RFC-RES] stdlnr=" + stdlnr
                        + " ok=" + rfcOk + " tknum=" + tknum + " mock=" + isMock + " msg=" + msg);

                row.put("ok", rfcOk);
                row.put("tknum", tknum);
                row.put("mock", isMock);
                row.put("svbeln_cnt", vbelnList.size());

                // 3) RFC 성공 시 WMS_IFC301 호출 + SAP 선적번호(SHPDI.STKNUM) 기록
                // ※ SAP 선적번호는 SHPDI.STKNUM 에 저장한다. (운영 PS_DISPATCH_H 에는
                //   STKNUM 컬럼이 없어 조회/저장 모두 SHPDI.STKNUM 을 사용)
                if (rfcOk) {
                    if (!tknum.isEmpty()) {
                        Map<String, Object> wms = callWmsIfc301(stdlnr, tknum, "C", env);
                        row.put("wms_result", wms);
                        try {
                            wmsJdbc.update(
                                "UPDATE KNRAWMS.SHPDI SET STKNUM=?, LMODAT=?, LMOUSR='WEB' " +
                                "WHERE STATIT='NEW' AND STDLNR=?",
                                tknum, today, stdlnr
                            );
                        } catch (Exception dbEx) {
                            row.put("db_update_err", dbEx.getMessage());
                            stdoutLog("[shipment-create][DB-ERR] stdlnr=" + stdlnr
                                    + " tknum=" + tknum + " error=" + dbEx.getMessage());
                            log.error("[shipment-create] SHPDI STKNUM 업데이트 실패: {} / stdlnr={} tknum={}",
                                dbEx.getMessage(), stdlnr, tknum);
                        }
                    } else {
                        msg = (msg + " [경고: SAP TKNUM 미반환]").trim();
                    }
                }
                row.put("message", msg);
            } catch (Exception ex) {
                stdoutLog("[shipment-create][EXC] stdlnr=" + stdlnr + " error=" + ex.getMessage());
                log.error("선적생성 처리 오류: stdlnr={}, msg={}", stdlnr, ex.getMessage(), ex);
                row.put("ok", false);
                row.put("message", ex.getMessage());
            }
            results.add(row);
        }

        boolean allOk = !results.isEmpty() && results.stream().allMatch(r -> Boolean.TRUE.equals(r.get("ok")));
        return Map.of("ok", allOk, "results", results, "env", env);
    }

    // ════════════════════════════════════════════════════════════════
    //  선적 삭제 (GUBUN='D')
    // ════════════════════════════════════════════════════════════════

    /**
     * SAP 선적삭제 (I_GUBUN='D').
     * Flask api_ps_sap_shipment_delete 이식.
     *
     * body : { items: [{ stdlnr, tknum }, ...] }
     *   - stdlnr : 가선적번호 (DISPATCH_NO)
     *   - tknum  : SAP 선적번호 (= 선적목록의 "SAP 선적번호" 컬럼값, PS_DISPATCH_H.STKNUM)
     *   가선적번호 단위로:
     *     1) RFC Z_TMS_SHIPMENT_CRDL(I_GUBUN='D', I_TKNUM=선적번호) 호출
     *     2) RFC 성공 시 WMS_IFC301 공통처리 API 호출
     *     3) PS_DISPATCH_H.STKNUM = NULL 초기화
     *
     * 반환 : { ok, results:[{stdlnr, tknum, ok, mock, message, wms_result, db_update_err, env}], env }
     */
    @Transactional
    public Map<String, Object> shipmentDelete(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) return err("items 필수 [{stdlnr, tknum}, ...]");

        String env   = detectEnv();
        String today = LocalDate.now().format(YMDFORMAT);
        List<Map<String, Object>> results = new ArrayList<>();

        for (Map<String, Object> item : items) {
            String stdlnr = str(item.get("stdlnr"));
            String tknum  = str(item.get("tknum"));

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stdlnr", stdlnr);
            row.put("tknum", tknum);
            row.put("env", env);

            if (tknum.isEmpty()) {
                row.put("ok", false);
                row.put("message", "SAP 선적번호(TKNUM)가 없습니다. 선적생성 후 삭제하세요.");
                results.add(row);
                continue;
            }

            try {
                // 1) RFC 호출 (I_GUBUN='D', I_TKNUM=선적번호)
                stdoutLog("[shipment-delete][RFC-REQ] stdlnr=" + stdlnr + " gubun=D tknum=" + tknum);
                Map<String, Object> rfc = callSapRfcShipment("D", Collections.emptyList(), tknum);
                boolean rfcOk  = Boolean.TRUE.equals(rfc.get("ok"));
                boolean isMock = Boolean.TRUE.equals(rfc.get("mock"));
                String  msg    = rfcMessage(rfc);
                stdoutLog("[shipment-delete][RFC-RES] stdlnr=" + stdlnr
                        + " ok=" + rfcOk + " mock=" + isMock + " msg=" + msg);

                row.put("ok", rfcOk);
                row.put("mock", isMock);
                row.put("message", msg);

                // 2) RFC 성공 시 WMS_IFC301 호출 + SAP 선적번호(SHPDI.STKNUM) 초기화
                // ※ SAP 선적번호는 SHPDI.STKNUM 에 저장/초기화한다. (운영 PS_DISPATCH_H 에
                //   STKNUM 컬럼이 없어 조회/저장 모두 SHPDI.STKNUM 을 사용)
                if (rfcOk) {
                    Map<String, Object> wms = callWmsIfc301(stdlnr, tknum, "D", env);
                    row.put("wms_result", wms);
                    try {
                        wmsJdbc.update(
                            "UPDATE KNRAWMS.SHPDI SET STKNUM=' ', LMODAT=?, LMOUSR='WEB' " +
                            "WHERE STATIT='NEW' AND STDLNR=?",
                            today, stdlnr
                        );
                    } catch (Exception dbEx) {
                        row.put("db_update_err", dbEx.getMessage());
                        stdoutLog("[shipment-delete][DB-ERR] stdlnr=" + stdlnr
                                + " tknum=" + tknum + " error=" + dbEx.getMessage());
                        log.error("[shipment-delete] SHPDI STKNUM 초기화 실패: {} / stdlnr={} tknum={}",
                            dbEx.getMessage(), stdlnr, tknum);
                    }
                }
            } catch (Exception ex) {
                stdoutLog("[shipment-delete][EXC] stdlnr=" + stdlnr + " tknum=" + tknum
                        + " error=" + ex.getMessage());
                log.error("선적삭제 처리 오류: stdlnr={}, tknum={}, msg={}", stdlnr, tknum, ex.getMessage(), ex);
                row.put("ok", false);
                row.put("message", ex.getMessage());
            }
            results.add(row);
        }

        boolean allOk = !results.isEmpty() && results.stream().allMatch(r -> Boolean.TRUE.equals(r.get("ok")));
        return Map.of("ok", allOk, "results", results, "env", env);
    }

    /** RFC 결과 Map 에서 E_RETURN.MESSAGE 추출 */
    @SuppressWarnings("unchecked")
    private String rfcMessage(Map<String, Object> rfc) {
        Object er = rfc.get("E_RETURN");
        if (er instanceof Map) {
            Object m = ((Map<String, Object>) er).get("MESSAGE");
            if (m != null) return m.toString();
        }
        return "";
    }

    // ════════════════════════════════════════════════════════════════
    //  배차삭제 — 선택 가선적번호(STDLNR)의 가배차 이력 삭제 (재배차 대상 복원)
    // ════════════════════════════════════════════════════════════════

    /**
     * 배차삭제: 선택한 가선적번호(STDLNR=DISPATCH_NO)의 가배차 이력을 삭제하여
     *           미배차(재배차 대상) 상태로 복원한다.
     * Flask api_ps_sap_delete 이식 (실운영 스키마 DISPATCH_NO / STKNUM 기준).
     *
     * body : { stknums: [STDLNR, ...] }
     *   ① SHPDI.STDLNR = ' '            (가선적번호 초기화 — NOT NULL 제약이므로 공백)
     *   ② SHPDH.VEHINO = ' ', CARTON=' ' (배차 차량유형 초기화 — NOT NULL 제약이므로 공백)
     *   ③ PS_DISPATCH_H.STATUS = 'CANCELLED'
     * 반환 : { ok, affected, stknums, restore_vehicles:[...] }  (배차탭 복원용)
     */
    @Transactional
    public Map<String, Object> sapDelete(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> stknumsRaw = (List<Object>) body.get("stknums");
        if (stknumsRaw == null || stknumsRaw.isEmpty()) return err("stknums 필수");

        List<String> stknums = stknumsRaw.stream()
            .map(this::str).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        if (stknums.isEmpty()) return err("stknums 필수");

        try {
            String inPh = String.join(",", Collections.nCopies(stknums.size(), "?"));
            Object[] args = stknums.toArray();
            String today = LocalDate.now().format(YMDFORMAT);

            // ① 삭제 전 PS_DISPATCH_H + D 에서 복원용 데이터 조회 (PS_DISPATCH_* → tmsJdbc)
            List<Map<String, Object>> dispRows = tmsJdbc.queryForList(
                "SELECT h.DISPATCH_NO, h.CARTYPE, h.RQSHPD, h.DPTNKY, h.DPTNM, " +
                "       h.TOTAL_KG, h.TOTAL_CNT " +
                "FROM KNRAWMS.PS_DISPATCH_H h WHERE h.DISPATCH_NO IN (" + inPh + ")", args
            );
            List<Map<String, Object>> dispDetail = tmsJdbc.queryForList(
                "SELECT d.DISPATCH_NO, d.SHPOKY, d.SHPOIT, d.SKUKEY, d.DESC01, " +
                "       d.QTSHPO, d.UOMKEY, d.DPTNKY, d.DPTNM, d.GRSWGT, d.KG_WEIGHT " +
                "FROM KNRAWMS.PS_DISPATCH_D d WHERE d.DISPATCH_NO IN (" + inPh + ") " +
                "ORDER BY d.DISPATCH_NO, d.SEQ", args
            );

            Map<String, List<Map<String, Object>>> itemsMap = new LinkedHashMap<>();
            for (Map<String, Object> r : dispDetail) {
                itemsMap.computeIfAbsent(str(r.get("DISPATCH_NO")), k -> new ArrayList<>()).add(r);
            }

            List<Map<String, Object>> restoreVehicles = new ArrayList<>();
            for (Map<String, Object> r : dispRows) {
                String dn = str(r.get("DISPATCH_NO"));
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("DISPATCH_NO", dn);
                v.put("cartype",   str(r.get("CARTYPE")));
                v.put("rqshpd",    str(r.get("RQSHPD")));
                v.put("dptnky",    str(r.get("DPTNKY")));
                v.put("dptnm",     str(r.get("DPTNM")));
                v.put("total_kg",  r.get("TOTAL_KG"));
                v.put("total_cnt", r.get("TOTAL_CNT"));
                v.put("items",     itemsMap.getOrDefault(dn, Collections.emptyList()));
                restoreVehicles.add(v);
            }

            // ② 삭제 대상 SHPOKY 수집 (SHPDH.VEHINO 초기화용, Oracle KNRAWMS → wmsJdbc)
            List<Map<String, Object>> shpokyRows = wmsJdbc.queryForList(
                "SELECT DISTINCT SHPOKY FROM KNRAWMS.SHPDI " +
                "WHERE STATIT='NEW' AND STDLNR IN (" + inPh + ")", args
            );
            List<String> shpokyList = shpokyRows.stream()
                .map(r -> str(r.get("SHPOKY"))).filter(s -> !s.isEmpty()).collect(Collectors.toList());

            // ③ SHPDI.STDLNR → ' ' (기본값 공백 복원)
            int affected = wmsJdbc.update(
                "UPDATE KNRAWMS.SHPDI SET STDLNR=' ', LMODAT=?, LMOUSR='WEB' " +
                "WHERE STATIT='NEW' AND STDLNR IN (" + inPh + ")",
                concat(new Object[]{today}, args)
            );

            // ④ SHPDH.VEHINO / CARTON → ' ' (배차 차량유형 초기화)
            // ※ SHPDH 의 VEHINO/CARTON/CARNO/DRIVER/DRIVERCEL 컬럼은 Oracle 에서 NOT NULL 제약이라
            //   NULL 을 세팅하면 ORA-01407 이 발생한다. 배차저장(PsDispatchService) 과 동일하게
            //   NULL 대신 공백 1칸(' ')으로 복원한다. (SHPDI.STDLNR=' ' 복원과 동일 패턴)
            if (!shpokyList.isEmpty()) {
                String inPh2 = String.join(",", Collections.nCopies(shpokyList.size(), "?"));
                wmsJdbc.update(
                    "UPDATE KNRAWMS.SHPDH SET VEHINO=' ', CARTON=' ', LMODAT=?, LMOUSR='WEB' " +
                    "WHERE SHPOKY IN (" + inPh2 + ")",
                    concat(new Object[]{today}, shpokyList.toArray())
                );
            }

            // ⑤ PS_DISPATCH_H.STATUS → 'CANCELLED' (PS_DISPATCH_H → tmsJdbc)
            //  ※ 버그수정: PS_DISPATCH_H 에는 UPDDAT 컬럼이 없음(bad SQL grammar).
            //    최종수정일 컬럼은 LMODAT (INSERT 시에도 DISPATCH_NO,…,CREDAT,CRETIM,LMODAT 사용).
            tmsJdbc.update(
                "UPDATE KNRAWMS.PS_DISPATCH_H SET STATUS='CANCELLED', LMODAT=? " +
                "WHERE DISPATCH_NO IN (" + inPh + ")",
                concat(new Object[]{today}, args)
            );

            stdoutLog("[ps-sap-delete] 배차삭제 완료 stknums=" + stknums + " affected=" + affected);
            log.info("배차삭제 완료: stknums={}, affected={}", stknums, affected);

            return Map.of(
                "ok", true,
                "affected", affected,
                "stknums", stknums,
                "restore_vehicles", restoreVehicles
            );
        } catch (Exception e) {
            stdoutLog("[ps-sap-delete][ERR] stknums=" + stknums + " error=" + e.getMessage());
            return errMap(e);
        }
    }

    /** Object[] 앞에 헤드 인자를 이어붙인다 (SET ?, ... WHERE IN (?) 바인드 구성용) */
    private Object[] concat(Object[] head, Object[] tail) {
        Object[] out = new Object[head.length + tail.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(tail, 0, out, head.length, tail.length);
        return out;
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
            stdoutLog("[Z_TMS_SHIPMENT_CRDL][MOCK] gubun=" + gubun + " tknum=" + tknum
                    + " vbeln=" + vbelnList);
            return sapRfcMock(gubun, tknum, "mock=true");
        }

        stdoutLog("[Z_TMS_SHIPMENT_CRDL][REQ] gubun=" + gubun + " tknum=" + tknum
                + " vbeln=" + vbelnList);
        try {
            // 1) Destination 획득 (커넥션 풀에서 연결 대여)
            JCoDestination dest = JCoDestinationManager.getDestination(SapJcoConfig.DEST_NAME);

            // 2) RFC Function 객체 생성
            JCoFunction function = dest.getRepository().getFunction(RFC_SHIPMENT);
            if (function == null) {
                return err("RFC 함수를 찾을 수 없음: " + RFC_SHIPMENT);
            }

            // 3) IMPORT 파라미터 설정
            //    실제 RFC 인터페이스(Z_TMS_SHIPMENT_CRDL ABAP 소스 기준):
            //      IMPORTING  I_GUBUN(ZGUBUN3 OPTIONAL) 'C'=생성/'D'=삭제
            //                 I_TKNUM(TKNUM   OPTIONAL) 선적번호(삭제 시 사용)
            //                 I_VBELN(VBELN_VL OPTIONAL) 단건 납품문서(생성/삭제 로직에서 미사용)
            //      TABLES     T_VBELN(STRUCTURE ZSDS109) 납품번호 목록(생성 시 사용)
            //    → 생성(C): T_VBELN 만 사용, I_TKNUM/I_VBELN 은 미사용
            //      삭제(D): I_TKNUM 만 사용, T_VBELN/I_VBELN 은 미사용
            String iTknum = (tknum != null ? tknum : "");
            JCoParameterList imports = function.getImportParameterList();
            imports.setValue("I_GUBUN", gubun);
            imports.setValue("I_TKNUM", iTknum);
            // I_VBELN 은 실제 RFC 로직에서 사용되지 않는 OPTIONAL 파라미터 → 세팅하지 않음
            // (과거 코드가 공백을 넣던 부분 제거: 스펙상 불필요. 함수에 필드가 있어도 무해)

            // 4) TABLE T_VBELN 설정 (선적 생성 시만)
            //    실제 소스: LOOP AT T_VBELN → LT_DELIV-VBELN = T_VBELN-VBELN (LIKP-VBELN=VBELN_VL, CHAR10)
            //    VBELN_VL 은 앞자리 0 채움(우측정렬) → 문자 10자리 왼쪽 '0' 패딩
            List<String> tVbelnRows = new ArrayList<>();   // 로깅용 실제 전송 VBELN 값
            if ("C".equals(gubun) && vbelnList != null && !vbelnList.isEmpty()) {
                JCoTable tVbeln = function.getTableParameterList().getTable("T_VBELN");
                for (String vbeln : vbelnList) {
                    tVbeln.appendRow();
                    String vPadded = padVbeln10(vbeln);
                    tVbeln.setValue("VBELN", vPadded);
                    tVbelnRows.add(vPadded);
                }
            }

            // ── SAP RFC 요청 상세 로그 (가독성 좋게 STDOUT.LOG 기록) ──
            logRfcRequest(gubun, iTknum, tVbelnRows);

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

            // ── SAP RFC 응답 상세 로그 (E_RETURN 전체 필드 + E_TKNUM) ──
            logRfcResponse(gubun, ok, eTknum, eReturn);

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
            // ── RFC 예외 처리 (Flask _call_sap_rfc_shipment 동작 이식) ──
            //  네트워크/연결/로그온 오류  → mock fallback (기능이 끊기지 않도록)
            //  ABAP 예외 등 RFC 자체 오류 → 하드 에러 반환
            String key = e.getKey() != null ? e.getKey() : "";
            String msg = e.getMessage() != null ? e.getMessage() : "";
            log.error("SAP JCo RFC 호출 실패: gubun={}, key={}, msg={}", gubun, key, msg, e);
            stdoutLog("[Z_TMS_SHIPMENT_CRDL][ERR] gubun=" + gubun + " key=" + key + " msg=" + msg);

            if (isConnError(key + " " + msg)) {
                stdoutLog("[Z_TMS_SHIPMENT_CRDL][MOCK-FALLBACK] gubun=" + gubun
                        + " 사유(연결오류)=" + key);
                return sapRfcMock(gubun, tknum, "conn_error: "
                        + (msg.length() > 120 ? msg.substring(0, 120) : msg));
            }
            return Map.of(
                "ok",       false,
                "E_RETURN", Map.of("TYPE", "E", "CODE", key, "MESSAGE", msg),
                "E_TKNUM",  "",
                "mock",     false
            );
        } catch (Throwable t) {
            // JCo 네이티브 라이브러리(libsapjco3.so) 미로딩 시 발생하는
            //  NoClassDefFoundError / UnsatisfiedLinkError / ExceptionInInitializerError 등.
            //  (catch(Exception) 으로는 Error 계층을 잡지 못하므로 Throwable 로 확장)
            //  → 연결 불가와 동일하게 mock fallback 하여 기능 흐름을 유지한다.
            String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            log.error("SAP JCo RFC 호출 실패(런타임/네이티브): gubun={}, msg={}", gubun, msg, t);
            stdoutLog("[Z_TMS_SHIPMENT_CRDL][ERR-NATIVE] gubun=" + gubun + " msg=" + msg);
            stdoutLog("[Z_TMS_SHIPMENT_CRDL][MOCK-FALLBACK] gubun=" + gubun
                    + " 사유(JCo 라이브러리 미로딩)");
            return sapRfcMock(gubun, tknum, "jco_unavailable: "
                    + (msg.length() > 120 ? msg.substring(0, 120) : msg));
        }
    }

    /** RFC 오류 문자열이 네트워크/연결/로그온 계열인지 판정 (Flask is_conn_err 이식) */
    private boolean isConnError(String errText) {
        if (errText == null) return false;
        String s = errText.toLowerCase();
        String[] keys = { "connection", "timeout", "unreachable", "refused",
                          "network", "logon", "communication", "host",
                          "jco_communication_failure", "jco_system_failure",
                          "partner", "connect to" };
        for (String k : keys) if (s.contains(k)) return true;
        return false;
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

    /**
     * 배차확정(SAP전송) 탭 — 가선적번호(STDLNR) 목록.
     *
     * ■ 조회 기준 (Flask api_ps_sap_list 이식)
     *   SHPDI(SI).STATIT='NEW' AND TRIM(SI.STDLNR)!=''  → 가선적번호가 채번된 모든 배차
     *   ※ STATUS(DRAFT/CONFIRMED/SAP_CREATED) 로 필터하지 않는다.
     *     배차삭제·SAP 선적생성 대상에는 DRAFT 배차도 포함되어야 하기 때문.
     *     (SAP 선적 생성 여부는 PH.STKNUM(SAP_STKNUM) 값 유무로 화면에서 구분)
     *
     * ■ 반환 필드 (프론트 _sapRenderLeft 기대 스키마)
     *   STDLNR, SAP_STKNUM, SVBELN_CNT, SHPOKY_CNT, ITEM_CNT, TOTAL_KG,
     *   RQSHPD_FROM, RQSHPD_TO, CARNO, VEHINO, DRIVER, DRIVERCEL, TDLNR, LMODAT,
     *   DPTNKY, DPTNKYNM, CARTYPE(명칭), CARCLASS_CD(코드)
     *
     * ■ DataSource: SHPDI/SHPDH/SKUMA/BZPTN/CMCDV/PS_DISPATCH_H → Oracle KNRAWMS (wmsJdbc)
     */
    public Map<String, Object> sapList(Map<String, Object> body) {
        try {
            // 프론트는 rqshpd_from/rqshpd_to/stknum/dptnky 로 전송 (dateFrom/dateTo 아님)
            String rqFrom  = firstNonEmpty(str(body.get("rqshpd_from")), str(body.get("dateFrom"))).replace("-", "");
            String rqTo    = firstNonEmpty(str(body.get("rqshpd_to")),   str(body.get("dateTo"))).replace("-", "");
            String stknum  = str(body.get("stknum"));
            String dptnky  = str(body.get("dptnky"));

            // ── 차량유형 코드↔명칭 맵 (CMCDV: CMCDKY='TMS_CARCLASS10') ──
            Map<String, String> code2name = new HashMap<>(); // Z010 → 1톤
            Map<String, String> name2code = new HashMap<>(); // 1톤 → Z010
            for (Map<String, Object> cc : wmsJdbc.queryForList(
                    "SELECT CMCDVL, CDESC1 FROM KNRAWMS.CMCDV WHERE CMCDKY='TMS_CARCLASS10'")) {
                String code = str(cc.get("CMCDVL"));
                String name = str(cc.get("CDESC1"));
                if (!code.isEmpty()) code2name.put(code, name);
                if (!name.isEmpty()) name2code.put(name, code);
            }

            // ── 동적 WHERE ──
            List<String> where = new ArrayList<>();
            where.add("SI.STATIT = 'NEW'");
            where.add("SI.STDLNR != ' '");
            List<Object> args = new ArrayList<>();
            if (!rqFrom.isEmpty()) { where.add("SH.RQSHPD >= ?"); args.add(rqFrom); }
            if (!rqTo.isEmpty())   { where.add("SH.RQSHPD <= ?"); args.add(rqTo); }
            if (!stknum.isEmpty()) { where.add("SI.STDLNR LIKE ?"); args.add("%" + stknum + "%"); }
            if (!dptnky.isEmpty()) {
                where.add("(SH.DPTNKY LIKE ? OR CT.NAME01 LIKE ?)");
                args.add("%" + dptnky + "%"); args.add("%" + dptnky + "%");
            }
            String whereSql = String.join(" AND ", where);

            String sql =
                "SELECT " +
                "  SI.STDLNR AS STDLNR, " +
                // SAP 선적번호: RFC 선적생성 후 SHPDI.STKNUM 에 저장된 E_TKNUM.
                // ※ 운영 DB 의 PS_DISPATCH_H 에는 STKNUM 컬럼이 없어 PH.STKNUM 참조 시
                //   ORA-00904(bad SQL grammar) 가 발생한다. 확실히 존재하는 SHPDI.STKNUM 을
                //   SAP 선적번호 저장소로 사용한다. (선적생성/삭제도 동일하게 SHPDI.STKNUM 갱신)
                //   SHPDI 는 STDLNR 당 여러 행 → MAX 집계 (GROUP BY SI.STDLNR 유지)
                "  NULLIF(TRIM(COALESCE(MAX(SI.STKNUM), '')), '') AS SAP_STKNUM, " +
                "  COUNT(DISTINCT SI.SVBELN) AS SVBELN_CNT, " +
                "  COUNT(DISTINCT SI.SHPOKY) AS SHPOKY_CNT, " +
                "  COUNT(*) AS ITEM_CNT, " +
                // 납품수량: 선적(STDLNR) 내 전체 아이템 출고수량 합계
                "  ROUND(SUM(COALESCE(SI.QTSHPO,0)),3) AS QTSHPO_SUM, " +
                // 단위: 선적 내 단위가 단일이면 그 값, 복수면 '(혼합)' 표기
                "  CASE WHEN COUNT(DISTINCT NULLIF(TRIM(SI.UOMKEY),'')) > 1 " +
                "       THEN '(혼합)' " +
                "       ELSE MAX(NULLIF(TRIM(SI.UOMKEY),'')) END AS UOMKEY, " +
                "  COALESCE(MAX(PH.TOTAL_KG), ROUND(SUM(SI.QTSHPO * COALESCE(M.NETWGT,0)),1)) AS TOTAL_KG, " +
                "  MIN(SH.RQSHPD) AS RQSHPD_FROM, " +
                "  MAX(SH.RQSHPD) AS RQSHPD_TO, " +
                "  MAX(NULLIF(TRIM(SH.CARTON),'')) AS CARTON, " +
                "  MAX(NULLIF(TRIM(SH.CARNO),''))  AS CARNO, " +
                "  MAX(NULLIF(TRIM(SH.VEHINO),'')) AS VEHINO, " +
                "  MAX(NULLIF(TRIM(SH.DRIVER),'')) AS DRIVER, " +
                "  MAX(NULLIF(TRIM(SH.DRIVERCEL),'')) AS DRIVERCEL, " +
                "  MAX(NULLIF(TRIM(SH.TDLNR),'')) AS TDLNR, " +
                "  MAX(SH.LMODAT) AS LMODAT, " +
                "  MAX(PH.CARTYPE) AS PH_CARTYPE, " +
                "  MAX(PH.STATUS)  AS PH_STATUS, " +
                "  CASE WHEN COUNT(DISTINCT SH.DPTNKY) > 1 " +
                "       THEN '(' || COUNT(DISTINCT SH.DPTNKY) || '개 납품처)' " +
                "       ELSE MAX(SH.DPTNKY) END AS DPTNKY, " +
                "  CASE WHEN COUNT(DISTINCT SH.DPTNKY) > 1 " +
                "       THEN '(' || COUNT(DISTINCT SH.DPTNKY) || '개 납품처)' " +
                "       ELSE MAX(COALESCE(CT.NAME01, SH.DPTNKY)) END AS DPTNKYNM " +
                "FROM KNRAWMS.SHPDI SI " +
                "JOIN KNRAWMS.SHPDH SH ON SI.SHPOKY = SH.SHPOKY " +
                "LEFT JOIN KNRAWMS.SKUMA M  ON M.SKUKEY  = SI.SKUKEY " +
                "LEFT JOIN KNRAWMS.BZPTN CT ON CT.PTNRKY = SH.DPTNKY AND CT.PTNRTY = 'CT' " +
                "LEFT JOIN KNRAWMS.PS_DISPATCH_H PH ON PH.DISPATCH_NO = SI.STDLNR " +
                "WHERE " + whereSql + " " +
                "GROUP BY SI.STDLNR " +
                "ORDER BY MIN(SH.RQSHPD) DESC, SI.STDLNR";

            List<Map<String, Object>> rows = wmsJdbc.queryForList(sql, args.toArray());

            // ── 차량유형 코드/명칭 정규화 (Flask 로직 이식) ──
            for (Map<String, Object> d : rows) {
                String cartype = "", carclassCd = "";

                String phCartype = str(d.get("PH_CARTYPE"));
                if (!phCartype.isEmpty()) {
                    cartype = phCartype;
                    carclassCd = name2code.getOrDefault(phCartype, "");
                }
                if (cartype.isEmpty()) {
                    String carton = str(d.get("CARTON"));
                    if (!carton.isEmpty()) {
                        if (code2name.containsKey(carton))      { carclassCd = carton; cartype = code2name.get(carton); }
                        else if (name2code.containsKey(carton)) { cartype = carton; carclassCd = name2code.get(carton); }
                        else                                    { cartype = carton; carclassCd = ""; }
                    }
                }
                if (cartype.isEmpty()) {
                    String vehino = str(d.get("VEHINO"));
                    if (!vehino.isEmpty()) {
                        if (code2name.containsKey(vehino))      { carclassCd = vehino; cartype = code2name.get(vehino); }
                        else if (name2code.containsKey(vehino)) { cartype = vehino; carclassCd = name2code.get(vehino); }
                        else                                    { cartype = vehino; carclassCd = ""; }
                    }
                }
                d.put("CARTYPE", cartype);
                d.put("CARCLASS_CD", carclassCd);
            }

            return Map.of("ok", true, "rows", rows, "total", rows.size());
        } catch (Exception e) { return errMap(e); }
    }

    private String firstNonEmpty(String a, String b) {
        return (a != null && !a.isEmpty()) ? a : (b == null ? "" : b);
    }

    public Map<String, Object> sapItems(Map<String, Object> body) {
        // ── 우선순위1: stknum(=SHPDI.STDLNR) 기반 적재뷰용 아이템 + 차량제원 ──
        //   프론트 _sapShowLoadImage 계약: {stknum, cartype} → {ok, items[], veh}
        //   (적재 시각화 2D/3D 렌더러가 대문자 SKU 아이템 스키마를 기대)
        String stknum = str(body.get("stknum"));
        if (!stknum.isEmpty()) {
            try {
                String sql =
                    "SELECT SI.SHPOKY, SI.SHPOIT, SI.SKUKEY, SI.DESC01, " +
                    "  SI.QTSHPO, SI.UOMKEY, " +
                    "  ROUND(SI.QTSHPO * COALESCE(M.NETWGT,0), 1) AS KG_WEIGHT, " +
                    "  COALESCE(M.NETWGT, 0) AS GRSWGT, " +
                    "  SH.DPTNKY, COALESCE(CT.NAME01, SH.DPTNKY) AS DPTNM " +
                    "FROM KNRAWMS.SHPDI SI " +
                    "JOIN KNRAWMS.SHPDH SH ON SI.SHPOKY = SH.SHPOKY " +
                    "LEFT JOIN KNRAWMS.SKUMA M  ON M.SKUKEY  = SI.SKUKEY " +
                    "LEFT JOIN KNRAWMS.BZPTN CT ON CT.PTNRKY = SH.DPTNKY " +
                    "WHERE SI.STATIT = 'NEW' AND SI.STDLNR = ? " +
                    "ORDER BY SI.SVBELN, SI.SHPOKY, CAST(SI.SHPOIT AS INTEGER)";
                List<Map<String, Object>> items = wmsJdbc.queryForList(sql, stknum);

                // 차량 제원(veh): cartype 기준 DS_VEHICLE(MariaDB) 1건
                Map<String, Object> veh = null;
                String cartype = str(body.get("cartype"));
                if (!cartype.isEmpty()) {
                    try {
                        List<Map<String, Object>> vs = tmsJdbc.queryForList(
                            "SELECT CARTYPE, LENGTH_M, WIDTH_M, HEIGHT_M, LOAD_TON, PALLET_HEIGHT_M " +
                            "FROM KNRAWMS.DS_VEHICLE WHERE CARTYPE=? FETCH FIRST 1 ROWS ONLY", cartype);
                        if (!vs.isEmpty()) veh = vs.get(0);
                    } catch (Exception ignore) { /* veh 없으면 프론트 기본치수 */ }
                }
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("ok", true);
                out.put("items", items);
                out.put("veh", veh);
                return out;
            } catch (Exception e) { return errMap(e); }
        }

        // ── 우선순위2(하위호환): disp_h_id 기반 PS_DISPATCH_D 원시행 ──
        Long dispHId = toLong(body.get("disp_h_id"));
        if (dispHId == null) return err("stknum 또는 disp_h_id 필수");
        try {
            List<Map<String, Object>> rows = tmsJdbc.queryForList(
                "SELECT d.*, h.CARTYPE, h.DISP_DATE FROM KNRAWMS.PS_DISPATCH_D d " +
                "JOIN KNRAWMS.PS_DISPATCH_H h ON h.DISP_H_ID=d.DISP_H_ID " +
                "WHERE d.DISP_H_ID=? ORDER BY d.ITEM_SEQ", dispHId
            );
            return Map.of("ok", true, "rows", rows);
        } catch (Exception e) { return errMap(e); }
    }

    /**
     * 선택한 가선적번호(STDLNR)에 매핑된 납품문서 상세 목록.
     * Flask: api_ps_sap_docs 이식.
     *   입력 : { stknum }  (= SHPDI.STDLNR 값)
     *   기준 : SI.STATIT='NEW' AND SI.STDLNR = ?   (STATUS 무관 → DRAFT 포함)
     *   반환 : 프론트 _sapDocColDefs 기대 컬럼
     *          (SVBELN/SHPOKY/SHPOIT/RQSHPD/DPTNKYNM/SKUKEY/DESC01/QTSHPO/UOMKEY/LINE_KG 등)
     *
     * ■ DataSource: SHPDI/SHPDH/SKUMA/BZPTN → Oracle KNRAWMS (wmsJdbc)
     */
    public Map<String, Object> sapDocs(Map<String, Object> body) {
        String stknum = str(body.get("stknum"));   // UI에서 STKNUM 키로 전달 (= STDLNR 값)
        if (stknum.isEmpty()) return err("stknum 필수");
        try {
            String sql =
                "SELECT " +
                "  SI.STDLNR AS STKNUM, " +
                "  SI.SVBELN, SI.SHPOKY, SI.SHPOIT, SI.STATIT, SI.SKUKEY, SI.DESC01, " +
                "  SI.QTSHPO, SI.UOMKEY, SI.QTSHPD, " +
                "  ROUND(SI.QTSHPO * COALESCE(M.NETWGT,0), 1) AS LINE_KG, " +
                "  COALESCE(M.NETWGT, 0) AS NETWGT, " +
                "  SH.RQSHPD, SH.DPTNKY, " +
                "  COALESCE(CT.NAME01, SH.DPTNKY) AS DPTNKYNM, " +
                "  SH.SHPMTY, SH.CARTON, SH.CARNO, SH.VEHINO, SH.DRIVER, SH.DRIVERCEL " +
                "FROM KNRAWMS.SHPDI SI " +
                "JOIN KNRAWMS.SHPDH SH ON SI.SHPOKY = SH.SHPOKY " +
                "LEFT JOIN KNRAWMS.SKUMA M  ON M.SKUKEY  = SI.SKUKEY " +
                "LEFT JOIN KNRAWMS.BZPTN CT ON CT.PTNRKY = SH.DPTNKY " +
                "WHERE SI.STATIT = 'NEW' AND SI.STDLNR = ? " +
                "ORDER BY SI.SVBELN, SI.SHPOKY, CAST(SI.SHPOIT AS INTEGER)";
            List<Map<String, Object>> rows = wmsJdbc.queryForList(sql, stknum);
            return Map.of("ok", true, "rows", rows, "total", rows.size());
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
                "UPDATE KNRAWMS.PS_DISPATCH_H SET VHCLNO=?, DRIVER_NM=?, DRIVER_TEL=?, LMODAT=? WHERE DISP_H_ID=?",
                str(body.get("vhclno")), str(body.get("driver_nm")), str(body.get("driver_tel")),
                LocalDate.now().format(YMDFORMAT), dispHId
            );
            return Map.of("ok", true);
        } catch (Exception e) { return errMap(e); }
    }

    // ════════════════════════════════════════════════════════════════
    //  WMS_IFC301 공통처리 API 호출 / 환경 감지 / STDOUT.LOG
    // ════════════════════════════════════════════════════════════════

    /**
     * 실행 환경 감지.
     *   시스템프로퍼티/환경변수 APP_ENV|SPRING_PROFILES_ACTIVE 가 production/prod → 'prod'
     *   그 외 → 'dev'
     */
    private String detectEnv() {
        String v = firstNonEmpty(
            System.getProperty("app.env"),
            firstNonEmpty(System.getenv("APP_ENV"),
                firstNonEmpty(System.getProperty("spring.profiles.active"),
                    System.getenv("SPRING_PROFILES_ACTIVE")))).toLowerCase();
        return (v.contains("prod")) ? "prod" : "dev";
    }

    private String wmsIfcUrl(String env) {
        return "prod".equals(env) ? WMS_IFC_URL_PROD : WMS_IFC_URL_DEV;
    }

    /**
     * 선적생성/삭제 후 WMS_IFC301 공통처리 API 호출.
     *   payload: { GUBUN, STDLNR, TKNUM }
     */
    private Map<String, Object> callWmsIfc301(String stdlnr, String tknum, String gubun, String env) {
        String url = wmsIfcUrl(env);
        String gubunLabel = "C".equals(gubun) ? "선적생성" : ("D".equals(gubun) ? "선적삭제" : gubun);
        String payload = String.format(
            "{\"GUBUN\":\"%s\",\"STDLNR\":\"%s\",\"TKNUM\":\"%s\"}",
            jsonEsc(gubun), jsonEsc(stdlnr), jsonEsc(tknum));

        // ── WMS_IFC301 요청 상세 로그 (가독성 좋게 STDOUT.LOG 기록) ──
        StringBuilder reqLog = new StringBuilder();
        reqLog.append(System.lineSeparator());
        reqLog.append("┌──────────────── WMS API 요청 (WMS_IFC301) ────────────────").append(System.lineSeparator());
        reqLog.append("│ ENV     : ").append(env).append(System.lineSeparator());
        reqLog.append("│ METHOD  : POST").append(System.lineSeparator());
        reqLog.append("│ URL     : ").append(url).append(System.lineSeparator());
        reqLog.append("│ [BODY]").append(System.lineSeparator());
        reqLog.append("│   GUBUN  = '").append(gubun).append("' (").append(gubunLabel).append(")").append(System.lineSeparator());
        reqLog.append("│   STDLNR = '").append(stdlnr).append("'  (가선적번호)").append(System.lineSeparator());
        reqLog.append("│   TKNUM  = '").append(tknum).append("'  (SAP 선적번호)").append(System.lineSeparator());
        reqLog.append("└────────────────────────────────────────────────────────────");
        stdoutLog(reqLog.toString());
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int sc = resp.statusCode();
            String bodyText = resp.body() == null ? "" : resp.body();
            String snippet = bodyText.length() > 500 ? bodyText.substring(0, 500) : bodyText;
            boolean ok = sc >= 200 && sc < 300;

            // ── WMS_IFC301 응답 상세 로그 ──
            StringBuilder resLog = new StringBuilder();
            resLog.append(System.lineSeparator());
            resLog.append("┌──────────────── WMS API 응답 (WMS_IFC301) ────────────────").append(System.lineSeparator());
            resLog.append("│ URL     : ").append(url).append(System.lineSeparator());
            resLog.append("│ 성공여부 : ").append(ok ? "성공" : "실패").append(System.lineSeparator());
            resLog.append("│ STATUS  : ").append(sc).append(System.lineSeparator());
            resLog.append("│ BODY    : ").append(snippet.isEmpty() ? "(없음)" : snippet).append(System.lineSeparator());
            resLog.append("└────────────────────────────────────────────────────────────");
            stdoutLog(resLog.toString());
            log.info("WMS_IFC301 호출: ok={}, status={}, stdlnr={}, tknum={}, gubun={}", ok, sc, stdlnr, tknum, gubun);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("ok", ok);
            r.put("status_code", sc);
            r.put("body", snippet);
            return r;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            StringBuilder errLog = new StringBuilder();
            errLog.append(System.lineSeparator());
            errLog.append("┌──────────────── WMS API 오류 (WMS_IFC301) ────────────────").append(System.lineSeparator());
            errLog.append("│ URL     : ").append(url).append(System.lineSeparator());
            errLog.append("│ STDLNR  : ").append(stdlnr).append(" / TKNUM : ").append(tknum).append(" / GUBUN : ").append(gubun).append(System.lineSeparator());
            errLog.append("│ ERROR   : ").append(ex.getMessage()).append(System.lineSeparator());
            errLog.append("└────────────────────────────────────────────────────────────");
            stdoutLog(errLog.toString());
            log.error("WMS_IFC301 호출 실패: stdlnr={}, tknum={}, msg={}", stdlnr, tknum, ex.getMessage(), ex);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("ok", false);
            r.put("error", ex.getMessage());
            return r;
        }
    }

    /**
     * RFC / API 호출·에러 로그를 기록.
     * (1) 애플리케이션 표준 로거(SLF4J/logback)에 INFO 로 남겨 콘솔/앱 로그에서 바로 확인
     * (2) 추가로 STDOUT.LOG 파일에도 append (파일 기록 실패해도 예외 미전파)
     */
    private synchronized void stdoutLog(String line) {
        // (1) 표준 로거 — 사용자가 보는 콘솔/logback 로그에 그대로 노출
        log.info("{}", line);

        // (2) 별도 STDOUT.LOG 파일에도 타임스탬프와 함께 기록
        String stamp = "[" + LocalDateTime.now().format(LOG_TS) + "] " + line + System.lineSeparator();
        try {
            Path p = Paths.get(STDOUT_LOG_PATH);
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            Files.writeString(p, stamp, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            // 파일 로깅 실패는 무시 (표준 로거에는 이미 남았으므로 흐름 방해 금지)
            log.warn("STDOUT.LOG 파일 기록 실패({}): {}", STDOUT_LOG_PATH, e.getMessage());
        }
    }

    private String jsonEsc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
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

    /**
     * 납품번호(VBELN)를 SAP 스펙(C(10))에 맞춰 10자리 문자로 정규화.
     * - 앞뒤 공백 제거 후 왼쪽 '0' 패딩 (SAP는 납품번호를 우측정렬 0-패딩 저장)
     * - 이미 10자리 이상이면 그대로 사용(값 손상 방지: 숫자 파싱하지 않음)
     */
    private String padVbeln10(String vbeln) {
        String v = (vbeln == null) ? "" : vbeln.trim();
        if (v.length() >= 10) return v;
        StringBuilder sb = new StringBuilder();
        for (int i = v.length(); i < 10; i++) sb.append('0');
        sb.append(v);
        return sb.toString();
    }

    /**
     * SAP RFC 요청 상세를 가독성 좋게 STDOUT.LOG 에 기록.
     * RFC 함수명 / IMPORT 파라미터 / TABLE(T_VBELN) 전송 내용을 한 블록으로 남긴다.
     */
    private void logRfcRequest(String gubun, String iTknum, List<String> tVbelnRows) {
        String gubunLabel = "C".equals(gubun) ? "선적생성" : ("D".equals(gubun) ? "선적삭제" : gubun);
        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator());
        sb.append("┌──────────────── SAP RFC 요청 ────────────────").append(System.lineSeparator());
        sb.append("│ FUNCTION : ").append(RFC_SHIPMENT).append(System.lineSeparator());
        sb.append("│ 구분      : ").append(gubun).append(" (").append(gubunLabel).append(")").append(System.lineSeparator());
        sb.append("│ [IMPORT]").append(System.lineSeparator());
        sb.append("│   I_GUBUN = '").append(gubun).append("'").append(System.lineSeparator());
        sb.append("│   I_TKNUM = '").append(iTknum).append("'").append(System.lineSeparator());
        sb.append("│   I_VBELN = (미전송, OPTIONAL 미사용)").append(System.lineSeparator());
        sb.append("│ [TABLE] T_VBELN  (STRUCTURE ZSDS109, ").append(tVbelnRows.size()).append("건)").append(System.lineSeparator());
        if (tVbelnRows.isEmpty()) {
            sb.append("│   (없음)").append(System.lineSeparator());
        } else {
            int i = 1;
            for (String v : tVbelnRows) {
                sb.append(String.format("│   %02d) VBELN = '%s'%n", i++, v));
            }
        }
        sb.append("└───────────────────────────────────────────────");
        stdoutLog(sb.toString());
    }

    /**
     * SAP RFC 응답 상세를 가독성 좋게 STDOUT.LOG 에 기록.
     * E_TKNUM 및 E_RETURN 구조체(TYPE/CODE/MESSAGE/MESSAGE_V1~V4)를 전부 남겨
     * "선적유형 는(은) 없습니다" 같은 메시지 치환변수(V1~V4) 원인 파악을 돕는다.
     */
    private void logRfcResponse(String gubun, boolean ok, String eTknum, JCoStructure eReturn) {
        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator());
        sb.append("┌──────────────── SAP RFC 응답 ────────────────").append(System.lineSeparator());
        sb.append("│ FUNCTION : ").append(RFC_SHIPMENT).append(" (구분 ").append(gubun).append(")").append(System.lineSeparator());
        sb.append("│ 성공여부  : ").append(ok ? "성공(S/I)" : "실패").append(System.lineSeparator());
        sb.append("│ [EXPORT]").append(System.lineSeparator());
        sb.append("│   E_TKNUM = '").append(eTknum != null ? eTknum : "").append("'").append(System.lineSeparator());
        sb.append("│ [EXPORT] E_RETURN").append(System.lineSeparator());
        String retMsg = "";
        for (String f : List.of("TYPE","CODE","MESSAGE","MESSAGE_V1","MESSAGE_V2","MESSAGE_V3","MESSAGE_V4")) {
            String v;
            try { v = eReturn.getString(f); } catch (JCoRuntimeException ex) { v = "(필드없음)"; }
            if ("MESSAGE".equals(f)) retMsg = nullSafe(v);
            sb.append(String.format("│   %-11s = '%s'%n", f, nullSafe(v)));
        }
        // ── 실패 시 진단 힌트 (실제 RFC 소스 분석 기반) ──
        if (!ok) {
            String hint = diagnoseRfcError(retMsg);
            if (!hint.isEmpty()) {
                sb.append("│ ▶ 진단힌트 : ").append(hint).append(System.lineSeparator());
            }
        }
        sb.append("└───────────────────────────────────────────────");
        stdoutLog(sb.toString());
    }

    /**
     * SAP RFC(Z_TMS_SHIPMENT_CRDL) 오류 메시지에 대한 진단 힌트.
     * 실제 ABAP 소스의 오류 분기 및 BAPI_SHIPMENT_CREATE 동작을 근거로,
     * WMS/TMS 코드가 아니라 SAP 마스터/업무 데이터 이슈임을 운영자에게 안내한다.
     */
    private String diagnoseRfcError(String msg) {
        if (msg == null) return "";
        String m = msg;
        if (m.contains("선적유형"))
            return "SAP가 선적유형(SHTYP)을 결정하지 못함. 실제 RFC 로직상 선행 판매오더의 유통경로(VBAK-VTWEG)가 '10'/'30'이 아니거나 미조회 시 SHTYP 공란→BAPI_SHIPMENT_CREATE 실패. 해당 납품문서의 VTWEG/출하지점(VSTEL) 마스터를 SAP 담당과 확인 필요";
        if (m.contains("출하지점을 찾을수 없") )
            return "납품문서(LIKP)의 출하지점(VSTEL) 미설정. SAP 납품문서 확인 필요";
        if (m.contains("출하지점이 서로 다른"))
            return "선택한 납품문서들의 출하지점(VSTEL)이 상이함. 동일 출하지점 납품문서끼리만 선적 가능";
        if (m.contains("운송경로를 찾을수 없"))
            return "납품문서(LIKP)의 운송경로(ROUTE) 미설정. SAP 납품문서 확인 필요";
        if (m.contains("운송경로는 직접 지정"))
            return "복수 운송경로 존재로 자동결정 불가. SAP측 운송경로 지정 필요";
        if (m.contains("선적문서가 생성된 납품문서"))
            return "이미 선적문서(VTTP)가 존재하는 납품문서. 중복 생성 불가";
        if (m.contains("납품문서가 없"))
            return "T_VBELN 이 비어 전송됨. WMS SHPDI.SVBELN 조회결과 확인 필요";
        return "";
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
