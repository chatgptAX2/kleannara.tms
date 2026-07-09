package com.company.module.wms.config;

import com.sap.conn.jco.ext.DestinationDataEventListener;
import com.sap.conn.jco.ext.DestinationDataProvider;
import com.sap.conn.jco.ext.Environment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.Properties;

/**
 * SAP JCo 커넥션 풀 설정
 *
 * ■ JCo 동작 원리
 *   - JCo 는 "Destination" 단위로 SAP 연결 정보를 관리
 *   - DestinationDataProvider 를 구현해 Environment 에 등록하면
 *     JCo 런타임이 JCoDestinationManager.getDestination(DEST_NAME) 호출 시
 *     이 프로바이더에서 Properties 를 조회
 *   - 커넥션 풀은 JCo 런타임이 내부적으로 관리 (pool-capacity / peak-limit 설정값 반영)
 *
 * ■ 라이브러리 위치 (서버)
 *   /data/tms/app/libs/sapjco3.jar       ← JCo Java 클래스
 *   /data/tms/app/libs/libsapjco3.so     ← JCo 네이티브 라이브러리 (JVM 로드 경로에 있어야 함)
 *
 * ■ 실행 시 JVM 옵션 (systemd tms.service ExecStart 에 추가)
 *   -Djava.library.path=/data/tms/app/libs
 *
 * ■ Mock 모드 (sap.jco.mock=true)
 *   @ConditionalOnProperty 로 이 빈 자체가 Spring 컨텍스트에 등록되지 않음.
 *   → JCo 클래스(MiddlewareJavaRfc 등)가 로딩되지 않으므로
 *     libsapjco3.so 가 없어도 UnsatisfiedLinkError 가 발생하지 않음.
 *   → SapRfcService 는 isMock() 체크로 JCo API 를 호출하지 않음.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sap.jco.mock", havingValue = "false", matchIfMissing = false)
public class SapJcoConfig {

    /** JCo Destination 이름 — SapRfcService 에서 이 이름으로 연결 */
    public static final String DEST_NAME = "TMS_SAP";

    private final SapJcoProperties props;

    @PostConstruct
    public void registerDestinationDataProvider() {
        try {
            TmsDestinationDataProvider provider = new TmsDestinationDataProvider(props);
            Environment.registerDestinationDataProvider(provider);
            log.info("[SAP JCo] Destination 등록 완료: name={}, ashost={}, sysnum={}, client={}",
                    DEST_NAME, props.getAshost(), props.getSysnum(), props.getClient());
        } catch (Throwable t) {
            // ── ExceptionInInitializerError / UnsatisfiedLinkError 처리 ──────────
            //  catch(Exception) 은 Error 계층을 잡지 못함.
            //  libsapjco3.so 가 java.library.path 에 없으면
            //  MiddlewareJavaRfc.<clinit> 에서 ExceptionInInitializerError(Error) 가 발생.
            //  Throwable 로 확장해야 기동 중단을 막을 수 있음.
            String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();

            if (msg.contains("already registered")) {
                // 재기동 시 이미 등록된 경우 — 정상
                log.info("[SAP JCo] DestinationDataProvider 이미 등록됨 — 재사용");

            } else if (t instanceof ExceptionInInitializerError
                    || t instanceof UnsatisfiedLinkError
                    || (t.getCause() instanceof UnsatisfiedLinkError)) {
                // libsapjco3.so 미설치 — 기동은 허용하되 SAP 기능 비활성
                log.error("[SAP JCo] 네이티브 라이브러리 로딩 실패 — SAP 연동이 비활성화됩니다.");
                log.error("[SAP JCo] 원인: {}", msg);
                log.error("[SAP JCo] 해결: /data/tms/app/libs/libsapjco3.so 배포 후");
                log.error("[SAP JCo]       systemd ExecStart 에 -Djava.library.path=/data/tms/app/libs 추가");
                log.error("[SAP JCo] 현재 java.library.path = {}",
                        System.getProperty("java.library.path"));
                // 기동 중단하지 않음 — SapRfcService 는 JCo 호출 시 예외 반환

            } else {
                log.error("[SAP JCo] DestinationDataProvider 등록 실패: {}", msg, t);
                throw new IllegalStateException("SAP JCo 초기화 실패", t);
            }
        }
    }

    // ── 내부 DestinationDataProvider 구현 ─────────────────────────
    private static class TmsDestinationDataProvider implements DestinationDataProvider {

        private final SapJcoProperties props;

        TmsDestinationDataProvider(SapJcoProperties props) {
            this.props = props;
        }

        @Override
        public Properties getDestinationProperties(String destinationName) {
            if (!DEST_NAME.equals(destinationName)) {
                throw new IllegalArgumentException("알 수 없는 SAP Destination: " + destinationName);
            }

            Properties p = new Properties();

            // ── 필수 접속 정보 ──────────────────────────────────────
            p.setProperty(DestinationDataProvider.JCO_ASHOST,  props.getAshost());
            p.setProperty(DestinationDataProvider.JCO_SYSNR,   props.getSysnum());
            p.setProperty(DestinationDataProvider.JCO_CLIENT,  props.getClient());
            p.setProperty(DestinationDataProvider.JCO_USER,    props.getUserid());
            p.setProperty(DestinationDataProvider.JCO_PASSWD,  props.getPasswd());
            p.setProperty(DestinationDataProvider.JCO_LANG,    props.getLangky());

            // ── 커넥션 풀 설정 ──────────────────────────────────────
            p.setProperty(DestinationDataProvider.JCO_POOL_CAPACITY,
                          String.valueOf(props.getPoolCapacity()));
            p.setProperty(DestinationDataProvider.JCO_PEAK_LIMIT,
                          String.valueOf(props.getPeakLimit()));

            return p;
        }

        @Override
        public boolean supportsEvents() {
            return false;
        }

        @Override
        public void setDestinationDataEventListener(DestinationDataEventListener eventListener) {
            // 이벤트 미지원
        }
    }
}
