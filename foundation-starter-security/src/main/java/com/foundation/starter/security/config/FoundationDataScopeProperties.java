package com.foundation.starter.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 数据范围配置（F-07 §13：{@code foundation.data-scope.*}）。 */
@ConfigurationProperties(prefix = "foundation.data-scope")
public class FoundationDataScopeProperties {

    /** 注解存在但快照缺失或取不到时的行为：{@code deny}（默认拒绝）。 */
    private String defaultMode = "deny";

    public String getDefaultMode() {
        return defaultMode;
    }

    public void setDefaultMode(String defaultMode) {
        this.defaultMode = defaultMode;
    }
}
