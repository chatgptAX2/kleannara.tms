package com.company.module.wms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SAP JCo 직접 연결 설정값
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
