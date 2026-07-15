package com.company.app.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * TMS 전용 DataSource (Oracle 19C KNRAWMS)
 *
 * ■ 패턴: @ConfigurationProperties + DataSourceBuilder (다중 DataSource 표준 패턴)
 *
 *   HikariCP 는 url 프로퍼티가 없고 jdbcUrl 만 존재.
 *   DataSourceBuilder 로 HikariDataSource 를 직접 생성할 때는
 *   Spring Boot 의 DataSourceProperties(url→jdbcUrl 변환)를 거치지 않으므로
 *   yml 에 반드시 jdbc-url 키를 사용해야 한다.
 *
 *   Spring Boot 공식 문서 인용:
 *     "Hikari has no url property. Instead, it has a jdbc-url property
 *      which means that you must rewrite your configuration"
 *     (https://docs.spring.io/spring-boot/how-to/data-access.html)
 *
 * ■ yml 설정 (prefix: datasource.tms)
 *
 *   datasource:
 *     tms:
 *       jdbc-url: jdbc:oracle:thin:@10.2.14.190:1522:KNMESWMS
 *       username: KNRAWMS
 *       password: kleannara12#
 *       driver-class-name: oracle.jdbc.OracleDriver
 *       hikari:
 *         pool-name: HikariPool-TMS
 *         maximum-pool-size: 20
 *         minimum-idle: 5
 *         connection-timeout: 30000
 *         idle-timeout: 600000
 *         max-lifetime: 1800000
 */
@Configuration
public class TmsDataSourceConfig {

    @Primary
    @Bean(name = "tmsDataSource")
    @ConfigurationProperties(prefix = "datasource.tms")
    public HikariDataSource tmsDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }
}
