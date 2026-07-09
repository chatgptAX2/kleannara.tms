package com.company.module.wms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SAP JCo 직접 연결 설정값
 *
 * ■ @Component 를 붙여 SapJcoConfig(@ConditionalOnProperty) 와 독립적으로
 *   항상 Spring 컨텍스트에 빈으로 등록되도록 한다.
 *
 *   문제 상황:
 *     @EnableConfigurationProperties(SapJcoProperties.class) 가 SapJcoConfig 에만 있었음.
 *     sap.jco.mock=true 시 @ConditionalOnProperty 로 SapJcoConfig 빈 자체가 미등록 →
 *     SapJcoProperties 빈도 미등록 → SapRfcService 생성자 주입 실패 → 기동 중단.
 *
 *   해결:
 *     @Component 를 직접 부여 → SapJcoConfig 등록 여부와 무관하게 항상 빈 등록.
 *     SapRfcService 는 mock 여부와 상관없이 SapJcoProperties 를 주입받아
 *     isMock() 으로 분기 처리 가능.
 *
 * application.yml 예시:
 * sap:
 *   jco:
 *     ashost: 10.2.14.210
 *     sysnum: "01"
 *     sysid:  DPQ
 *     client: "100"
 *     userid: WMS001
 *     passwd: Klean22709290
 *     langky: KO
 *     pool-capacity: 3        # 커넥션 풀 최대 크기 (기본 3)
 *     peak-limit: 10          # 최대 동시 연결 수 (기본 10)
 *     mock: false             # true = SAP 연결 없이 더미 응답
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "sap.jco")
public class SapJcoProperties {

    /** SAP 애플리케이션 서버 호스트 IP */
    private String ashost = "localhost";

    /** 시스템 번호 (예: "01") */
    private String sysnum = "00";

    /** 시스템 ID (예: DPQ) */
    private String sysid = "";

    /** 클라이언트 번호 (예: "100") */
    private String client = "100";

    /** SAP 로그인 유저 ID */
    private String userid = "";

    /** SAP 로그인 패스워드 */
    private String passwd = "";

    /** 로그인 언어 (KO = 한국어) */
    private String langky = "KO";

    /** JCo 커넥션 풀 최대 크기 */
    private int poolCapacity = 3;

    /** JCo 최대 동시 연결 수 */
    private int peakLimit = 10;

    /** true 시 SAP 연결 없이 Mock 응답 반환 */
    private boolean mock = true;
}
