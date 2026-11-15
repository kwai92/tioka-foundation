package com.foundation.starter.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Web 层接入配置（F-02 BAS-24 §foundation-starter-web：{@code foundation.web.*}）。
 *
 * <p>承载幂等键存活时长、请求日志开关与请求日志/脱敏使用的敏感键清单。敏感键按“包含”匹配，
 * 忽略大小写（例如 {@code client_secret} 命中 {@code client-secret}）。
 */
@ConfigurationProperties(prefix = "foundation.web")
public class FoundationWebProperties {

    /** 幂等键存活时长（配置项 {@code foundation.web.idempotency-ttl=10m}）。 */
    private Duration idempotencyTtl = Duration.ofMinutes(10);

    /** 是否开启请求访问日志（配置项 {@code foundation.web.request-log-enabled}）。 */
    private boolean requestLogEnabled = true;

    /** 请求日志与脱敏时按“包含”匹配的敏感键（忽略大小写）。 */
    private List<String> sensitiveKeys = List.of(
            "password", "passwd", "secret", "token", "authorization",
            "apikey", "api-key", "clientsecret", "client-secret", "credential");

    public Duration getIdempotencyTtl() {
        return idempotencyTtl;
    }

    public void setIdempotencyTtl(Duration idempotencyTtl) {
        this.idempotencyTtl = idempotencyTtl;
    }

    public boolean isRequestLogEnabled() {
        return requestLogEnabled;
    }

    public void setRequestLogEnabled(boolean requestLogEnabled) {
        this.requestLogEnabled = requestLogEnabled;
    }

    public List<String> getSensitiveKeys() {
        return sensitiveKeys;
    }

    public void setSensitiveKeys(List<String> sensitiveKeys) {
        this.sensitiveKeys = sensitiveKeys;
    }
}
