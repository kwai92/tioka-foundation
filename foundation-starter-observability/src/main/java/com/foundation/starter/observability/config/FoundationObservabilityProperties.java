package com.foundation.starter.observability.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 可观测接入配置（F-02 BAS-24 §foundation-starter-observability：{@code foundation.observability.*}）。
 */
@ConfigurationProperties(prefix = "foundation.observability")
public class FoundationObservabilityProperties {

    /** Trace 采样率（0~1）；实际生效由应用侧 SDK/agent 读取本约定值决定（对齐 GOV-08）。 */
    private double traceSampleRate = 0.1;

    /** 服务名（OpenTelemetry {@code service.name}）。 */
    private String serviceName = "foundation-app";

    public double getTraceSampleRate() {
        return traceSampleRate;
    }

    public void setTraceSampleRate(double traceSampleRate) {
        this.traceSampleRate = traceSampleRate;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
