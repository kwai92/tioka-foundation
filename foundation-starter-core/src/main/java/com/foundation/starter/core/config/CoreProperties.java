package com.foundation.starter.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 核心 starter 配置（F-02 BAS-24 §foundation-starter-core）。 */
@ConfigurationProperties(prefix = "foundation.core")
public class CoreProperties {

    /** 时间统一 UTC（00-施工总纲 §2.2）。 */
    private String timeZone = "UTC";

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }
}
