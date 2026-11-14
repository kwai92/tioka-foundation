package com.foundation.calendar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 工作日历配置（F-09 BAS-18 §18.1 §配置项 {@code foundation.calendar.*}）。
 *
 * <p>与 {@code CalendarAutoConfiguration} 同包（00-施工总纲 §2.5），全部带默认值：
 * 缺省区域 {@code CN}、结果按 {@code (region, year)} 缓存 10 分钟。
 */
@ConfigurationProperties(prefix = "foundation.calendar")
public class CalendarProperties {

    /** 默认区域：未配置区域的日历回退到此（§18.1 {@code regionFallback}）。 */
    private String defaultRegion = "CN";

    /** 单区域单年度日历的读取缓存 TTL（零或负值表示不缓存）。 */
    private Duration cacheTtl = Duration.ofMinutes(10);

    public String getDefaultRegion() {
        return defaultRegion;
    }

    public void setDefaultRegion(String defaultRegion) {
        this.defaultRegion = defaultRegion;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }
}
