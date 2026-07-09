package com.company.app.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * WMS 전용 DataSource (Oracle)
 *
 * ■ 패턴: @ConfigurationProperties + DataSourceBuilder (다중 DataSource 표준 패턴)
 *
 *   HikariCP 는 url 프로퍼티가 없고 jdbcUrl 만 존재.
 *   DataSourceBuilder 로 HikariDataSource 를 직접 생성할 때는
 *   Spring Boot 의 DataSourceProperties(url→jdbcUrl 변환)를 거치지 않으므로
 *   yml 에 반드시 jdbc-url 키를 사용해야 한다.
 *
 * ■ yml 설정 (prefix: datasource.wms)
 *
 *   datasource:
 *     wms:
 *       jdbc-url: jdbc:oracle:thin:@10.2.14.190:1522:KNMESWMS
 *       username: KNRATMS
 *       password: kleannara12#
 *       driver-class-name: oracle.jdbc.OracleDriver
 *       hikari:
 *         pool-name: HikariPool-WMS
 *         maximum-pool-size: 10
 *         minimum-idle: 3
 *         connection-timeout: 30000
 *         idle-timeout: 600000
 *         max-lifetime: 1800000
 */
@Configuration
public class WmsDataSourceConfig {

    @Bean(name = "wmsDataSource")
    @ConfigurationProperties(prefix = "datasource.wms")
    public HikariDataSource wmsDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }
}
