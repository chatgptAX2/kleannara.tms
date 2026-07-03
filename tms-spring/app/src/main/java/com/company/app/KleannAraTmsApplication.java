package com.company.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;

/**
 * Kleannara TMS Spring Boot 진입점
 *
 * 멀티모듈 플러그인 방식:
 *   @ComponentScan      → com.company.module.** Controller/Service 자동 등록
 *   @EntityScan         → com.company.module.** Entity 자동 스캔
 *   @EnableJpaRepositories → com.company.module.** Repository 자동 등록
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.company.core",
    "com.company.module"
})
@EntityScan(basePackages = "com.company.module")
@EnableJpaRepositories(basePackages = "com.company.module")
public class KleannAraTmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(KleannAraTmsApplication.class, args);
    }
}
