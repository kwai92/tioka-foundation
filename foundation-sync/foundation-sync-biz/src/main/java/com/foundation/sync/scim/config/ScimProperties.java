package com.foundation.sync.scim.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * SCIM 服务端配置（F-08 BAS-13 §4 {@code foundation.scim.*}）。
 *
 * <p>与 {@code ScimAutoConfiguration} 同包（00-施工总纲 §2.5），全部带默认值。
 */
@ConfigurationProperties(prefix = "foundation.scim")
public class ScimProperties {

    /** 停用累计窗口（缺省 10 分钟）：窗口内累计超阈值即暂停剩余停用。 */
    private Duration deactivateWindow = Duration.ofMinutes(10);

    /** {@code Idempotency-Key} 去重有效期（缺省 24 小时）。 */
    private Duration idempotencyTtl = Duration.ofHours(24);

    public Duration getDeactivateWindow() {
        return deactivateWindow;
    }

    public void setDeactivateWindow(Duration deactivateWindow) {
        this.deactivateWindow = deactivateWindow;
    }

    public Duration getIdempotencyTtl() {
        return idempotencyTtl;
    }

    public void setIdempotencyTtl(Duration idempotencyTtl) {
        this.idempotencyTtl = idempotencyTtl;
    }
}
