package com.foundation.starter.data.config;

import com.foundation.starter.data.flyway.FlywayNaming;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 迁移命名约定装配（F-02 BAS-24 §foundation-starter-data）。
 *
 * <p>与 {@link FoundationDataAutoConfiguration} 分开：后者要求存在 {@code SqlSessionFactory}
 * （{@code @ConditionalOnBean}），而迁移命名约定不依赖运行期数据源，构建期与纯配置测试也要可用。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "foundation", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FoundationDataProperties.class)
public class FoundationMigrationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FlywayNaming flywayNaming(FoundationDataProperties properties) {
        return new FlywayNaming(properties.getFlywayPrefix());
    }
}
