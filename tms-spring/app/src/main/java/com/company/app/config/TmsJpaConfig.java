package com.company.app.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * TMS DB (Oracle 19C — KNRAWMS) 전용 JPA 설정
 *
 * ■ TMS 테이블 Repository — basePackageClasses 로 정확한 클래스만 지정
 *   (basePackages 재귀 스캔 시 wms 서브패키지 Repository 중복 등록 방지)
 *   - DispatchObjectiveRepository : PS제약조건관리 — Oracle KNRAWMS
 *   - PsDispatchHRepository       : PS_DISPATCH_H  — Oracle KNRAWMS
 *   - DsVehicleRepository         : DS_VEHICLE     — Oracle KNRAWMS (vehicle.repository.tms)
 *   - RouteCostRepository         : ROUTE_COST     — Oracle KNRAWMS
 *
 * ■ Oracle WMS 전용 테이블은 WmsJpaConfig 에서 관리
 *   shipment(SHPDH/SHPDI), delivery(BZPTN_DETAIL), vehicle.wms(VHCMA) — Oracle KNRAWMS
 *
 * 트랜잭션 한정자: @Transactional("tmsTransactionManager") 또는 @Transactional (기본)
 */
@Configuration
@EnableJpaRepositories(
    basePackageClasses = {
        com.company.module.dispatchconfig.repository.DispatchObjectiveRepository.class, // PS제약조건관리 — Oracle KNRAWMS
        com.company.module.dispatch.repository.PsDispatchHRepository.class,             // PS_DISPATCH_H  — Oracle KNRAWMS
        com.company.module.vehicle.repository.tms.DsVehicleRepository.class,           // DS_VEHICLE     — Oracle KNRAWMS
        com.company.module.delivery.repository.tms.RouteCostRepository.class,          // ROUTE_COST     — Oracle KNRAWMS
        com.company.module.delivery.repository.wms.BzptnDetailRepository.class         // BZPTN_DETAIL   — TMS DB 소속
    },
    entityManagerFactoryRef = "tmsEntityManagerFactory",
    transactionManagerRef   = "tmsTransactionManager"
)
public class TmsJpaConfig {

    @Primary
    @Bean(name = "tmsEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean tmsEntityManagerFactory(
            @Qualifier("tmsDataSource") DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan(
            "com.company.module.dispatchconfig.entity",
            "com.company.module.dispatch.entity",            // PsDispatchH, PsDispatchI — Oracle KNRAWMS
            "com.company.module.vehicle.entity.tms",         // DsVehicle — Oracle KNRAWMS
            "com.company.module.delivery.entity.tms",        // RouteCost — Oracle KNRAWMS
            "com.company.module.delivery.entity.wms"         // BzptnDetail — TMS DB 소속
        );
        em.setPersistenceUnitName("tmsPU");

        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setGenerateDdl(false);
        em.setJpaVendorAdapter(adapter);

        Properties props = new Properties();
        props.setProperty("hibernate.dialect",
            "org.hibernate.dialect.OracleDialect");
        // Oracle 19C: ROWNUM / FETCH FIRST N ROWS ONLY 페이징 지원
        props.setProperty("hibernate.physical_naming_strategy",
            "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");
        props.setProperty("hibernate.hbm2ddl.auto", "none");
        props.setProperty("hibernate.show_sql",  "false");
        props.setProperty("hibernate.format_sql", "true");
        em.setJpaProperties(props);

        return em;
    }

    @Primary
    @Bean(name = "tmsTransactionManager")
    public PlatformTransactionManager tmsTransactionManager(
            @Qualifier("tmsEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
