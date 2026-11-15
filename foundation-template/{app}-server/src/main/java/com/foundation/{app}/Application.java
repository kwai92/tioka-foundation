package com.foundation.{app};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * {app} 应用入口（从 BAS-25 模板复制生成）。
 *
 * <p>只做启动装配：控制器不标 {@code @RestController}，由各模块的 AutoConfiguration
 * 以 {@code @Bean} 装配；扫描范围限定在本应用包内。
 */
@SpringBootApplication(scanBasePackages = "com.foundation.{app}")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
