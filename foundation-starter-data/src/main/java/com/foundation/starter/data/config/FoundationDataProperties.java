package com.foundation.starter.data.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据访问配置（F-02 BAS-24 §配置项）。
 *
 * <p>对应 {@code foundation.data.*}；Flyway/数据源本体仍由应用的 {@code spring.flyway} /
 * {@code spring.datasource} 配置，本类只承载底座侧的命名约定。
 */
@ConfigurationProperties(prefix = "foundation.data")
public class FoundationDataProperties {

    /** 迁移命名前缀（F-02 BAS-24 §配置项，默认 {@code V}）。 */
    private String flywayPrefix = "V";

    public String getFlywayPrefix() {
        return flywayPrefix;
    }

    public void setFlywayPrefix(String flywayPrefix) {
        this.flywayPrefix = flywayPrefix;
    }
}
