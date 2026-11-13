package com.foundation.starter.core.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 核心自动装配（F-02 BAS-24 §foundation-starter-core）。
 *
 * <p>本构件只做约定与配置绑定（时间 UTC、错误码、ID、请求上下文均为静态工具）；
 * Jackson 与全局异常等 web 级约定归 {@code foundation-starter-web}。
 */
@AutoConfiguration
@EnableConfigurationProperties({CoreProperties.class, FoundationProperties.class})
public class CoreAutoConfiguration {
}
