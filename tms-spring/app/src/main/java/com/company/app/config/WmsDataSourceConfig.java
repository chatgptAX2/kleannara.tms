package com.company.app.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * WMS 전용 DataSource (Oracle — 10.2.14.190:1522/KNMESWMS)
 *
 * 대상 모듈: PS배차, 출고예정정보, 공통코드, 물류센터, 납품처(BZPTN), 차량,
 *           출고오더(SHPDH/SHPDI), SKU(SKUMA), 단위구성 등 WMS DB 전체 조회/처리
 *
 * application.yml 설정:
 * datasource:
 *   wms:
 *     jdbc-url: jdbc:oracle:thin:@10.2.14.190:1522:KNMESWMS
 *     username: KNRATMS
 *     password: kleannara12#
 */
@Configuration
public class WmsDataSourceConfig {

    @Bean(name = "wmsDataSource")
    @ConfigurationProperties(prefix = "datasource.wms")
    public DataSource wmsDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }
}
