package com.company.app.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * WMS DB (Oracle KNRAWMS) 전용 JPA + JdbcTemplate 설정
 *
 * ■ Oracle 전용 Repository (KNRAWMS 스키마 테이블만)
 *   - com.company.module.shipment.repository       (SHPDH, SHPDI — Oracle KNRAWMS)
 *   - com.company.module.delivery.repository       (BZPTN_DETAIL — Oracle KNRAWMS)
 *   - com.company.module.vehicle.repository.wms    (VHCMA — Oracle KNRAWMS)
 *
 * ■ MariaDB 테이블은 TmsJpaConfig 에서 관리
 *   dispatch(PS_DISPATCH_H/D), vehicle(DS_VEHICLE), delivery.RouteCost(ROUTE_COST)
 *
 * WMS JdbcTemplate 빈:
 *   @Qualifier("wmsJdbcTemplate") 로 주입
 *
 * 트랜잭션 한정자: @Transactional("wmsTransactionManager")
 */
@Configuration
@EnableJpaRepositories(
    basePackages = {
        "com.company.module.shipment.repository",       // SHPDH, SHPDI — Oracle KNRAWMS
        "com.company.module.delivery.repository",       // BZPTN_DETAIL — Oracle KNRAWMS
        "com.company.module.vehicle.repository.wms"     // VHCMA — Oracle KNRAWMS
    },
    entityManagerFactoryRef = "wmsEntityManagerFactory",
    transactionManagerRef   = "wmsTransactionManager"
)
public class WmsJpaConfig {

    @Bean(name = "wmsEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean wmsEntityManagerFactory(
            @Qualifier("wmsDataSource") DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan(
            "com.company.module.shipment.entity",       // ShpdH, ShpdI — Oracle KNRAWMS
            "com.company.module.delivery.entity",       // BzptnDetail — Oracle KNRAWMS (RouteCost는 entity.tms 로 분리됨)
            "com.company.module.vehicle.entity.wms"     // Vhcma — Oracle KNRAWMS
        );
        em.setPersistenceUnitName("wmsPU");

        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setGenerateDdl(false);
        em.setJpaVendorAdapter(adapter);

        Properties props = new Properties();
        props.setProperty("hibernate.dialect",
            "org.hibernate.dialect.OracleDialect");
        props.setProperty("hibernate.physical_naming_strategy",
            "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");
        props.setProperty("hibernate.hbm2ddl.auto", "none");
        props.setProperty("hibernate.show_sql",  "false");
        props.setProperty("hibernate.format_sql", "true");
        // Oracle 기본 스키마: KNRATMS 계정으로 접속
        // KNRAWMS 스키마 테이블은 엔티티/쿼리에서 명시적 스키마 접두어 사용
        props.setProperty("hibernate.default_schema", "KNRATMS");
        em.setJpaProperties(props);

        return em;
    }

    @Bean(name = "wmsTransactionManager")
    public PlatformTransactionManager wmsTransactionManager(
            @Qualifier("wmsEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    /**
     * WMS DB 전용 JdbcTemplate
     * WMS 서비스에서 @Qualifier("wmsJdbcTemplate") 로 주입
     *
     * ■ wmsDataSource(Oracle) 를 명시적으로 주입
     *   — @Primary tmsDataSource(MariaDB) 가 자동 주입되는 것을 방지
     */
    @Bean(name = "wmsJdbcTemplate")
    public JdbcTemplate wmsJdbcTemplate(
            @Qualifier("wmsDataSource") DataSource dataSource) {
        JdbcTemplate jt = new JdbcTemplate(dataSource);
        jt.setQueryTimeout(30);   // Oracle 쿼리 타임아웃 30초
        return jt;
    }

    /**
     * TMS DB 전용 JdbcTemplate (기본 Bean 이름 충돌 방지용 명시 등록)
     */
    @Bean(name = "tmsJdbcTemplate")
    public JdbcTemplate tmsJdbcTemplate(
            @Qualifier("tmsDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
