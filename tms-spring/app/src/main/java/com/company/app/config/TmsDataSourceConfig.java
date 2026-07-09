package com.company.app.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * TMS 전용 DataSource (MariaDB — 10.2.14.247:3306/intergration)
 *
 * 대상 모듈: PS제약조건관리, 운송경로비용(BZPTN_DETAIL/ROUTE_COST), 서류관리(DOC_FOLDER/DOC_FILE)
 *
 * application.yml 설정:
 * datasource:
 *   tms:
 *     jdbc-url: jdbc:mariadb://10.2.14.247:3306/intergration?...
 *     username: ...
 *     password: ...
 */
@Configuration
public class TmsDataSourceConfig {

    @Primary
    @Bean(name = "tmsDataSource")
    @ConfigurationProperties(prefix = "datasource.tms")
    public DataSource tmsDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }
}
