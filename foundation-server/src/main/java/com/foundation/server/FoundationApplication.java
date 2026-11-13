package com.foundation.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Foundation 服务入口（F-02 BAS-01）。
 *
 * <p>包根 {@code com.foundation}：各领域 {@code -biz} 与通用公共域（Keycloak 管理接口客户端等）
 * 均在扫描范围内；跨模块编译期只依赖对方 {@code -api}（00-施工总纲 §2.1）。
 */
@SpringBootApplication(scanBasePackages = "com.foundation")
public class FoundationApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoundationApplication.class, args);
    }
}
