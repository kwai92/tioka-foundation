package com.foundation.common.dict.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 字典配置（F-02 BAS-09 §配置项）。 */
@ConfigurationProperties(prefix = "foundation.dict")
public class DictProperties {

    /** 服务端读取缓存（starter 侧缓存见 BAS-08）；零表示不缓存。 */
    private Duration cacheTtl = Duration.ofMinutes(5);

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }
}
