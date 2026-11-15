package com.foundation.starter.web.config;

import com.foundation.starter.core.config.FoundationProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置（F-02 BAS-24 §foundation-starter-web）。
 *
 * <p>仅在引入 springdoc（{@code io.swagger.v3.oas.models.OpenAPI} 在类路径）时装配，避免硬依赖；
 * 用字符串形式的 {@code @ConditionalOnClass} 做条件，缺类时本配置整体跳过。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "io.swagger.v3.oas.models.OpenAPI")
public class OpenApiConfig {

    /** 构造 OpenAPI 描述：标题/版本/应用编码与服务器基址。 */
    @Bean
    @ConditionalOnMissingBean
    public OpenAPI foundationOpenApi(FoundationProperties foundationProperties) {
        String appCode = foundationProperties.getAppCode();
        Info info = new Info()
                .title("Foundation 中台 API")
                .version("v1")
                .description("应用编码：" + (appCode == null ? "-" : appCode));
        Server server = new Server().url(foundationProperties.getBaseUrl());
        return new OpenAPI().info(info).addServersItem(server);
    }
}
