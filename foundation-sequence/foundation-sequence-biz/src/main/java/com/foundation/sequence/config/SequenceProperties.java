package com.foundation.sequence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 编号与序列配置（F-09 BAS-18 §18.2 {@code foundation.sequence.*}）。
 *
 * <p>与 {@code SequenceAutoConfiguration} 同包（00-施工总纲 §2.5），全部带默认值：
 * 幂等键 TTL 1 小时、单次取号上限 1000。
 */
@ConfigurationProperties(prefix = "foundation.sequence")
public class SequenceProperties {

    /** {@code Idempotency-Key} 去重窗口。 */
    private Duration idempotencyTtl = Duration.ofHours(1);

    /** 单次取号个数上限。 */
    private int maxCount = 1000;

    public Duration getIdempotencyTtl() {
        return idempotencyTtl;
    }

    public void setIdempotencyTtl(Duration idempotencyTtl) {
        this.idempotencyTtl = idempotencyTtl;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(int maxCount) {
        this.maxCount = maxCount;
    }
}
