package com.company.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.autoconfigure.domain.EntityScan;

/**
 * Kleannara TMS Spring Boot 진입점
 *
 * ■ 다중 DataSource 구조
 *   - TMS DB (MariaDB, Primary) : PS제약조건관리 / 운송경로비용 / 서류관리
 *   - WMS DB (Oracle)           : PS배차 / 출고예정 / 공통코드 / 물류센터 / 차량 / 납품처 등
 *
 * ■ 자동설정 제외
 *   DataSourceAutoConfiguration, HibernateJpaAutoConfiguration 을 exclude 하고
 *   TmsJpaConfig / WmsJpaConfig 에서 수동으로 각 DataSource/EntityManagerFactory 를 등록
 *
 * ■ @EnableJpaRepositories 는 각 JpaConfig 클래스에서 패키지별로 선언
 */
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
@ComponentScan(basePackages = {
    "com.company.app",
    "com.company.core",
    "com.company.module"
})
@EntityScan(basePackages = "com.company.module")
public class KleannAraTmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(KleannAraTmsApplication.class, args);
    }
}
