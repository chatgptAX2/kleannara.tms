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
 * TMS DB (MariaDB) 전용 JPA 설정
 *
 * 관리 패키지:
 *   - com.company.module.dispatchconfig  (PS제약조건관리)
 *   - com.company.module.document        (서류관리 — JdbcTemplate 사용, JPA 없음)
 *
 * !! delivery 패키지 (BZPTN_DETAIL, ROUTE_COST, BZPTN) 는 Oracle WMS DB 에 있으므로
 *    WmsJpaConfig 에서 관리함.
 *
 * 트랜잭션 한정자: @Transactional("tmsTransactionManager")
 */
@Configuration
@EnableJpaRepositories(
    basePackages = {
        "com.company.module.dispatchconfig.repository"
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
            "com.company.module.dispatchconfig.entity"
        );
        em.setPersistenceUnitName("tmsPU");

        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setGenerateDdl(false);
        em.setJpaVendorAdapter(adapter);

        Properties props = new Properties();
        props.setProperty("hibernate.dialect",
            "org.hibernate.dialect.MariaDBDialect");
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
