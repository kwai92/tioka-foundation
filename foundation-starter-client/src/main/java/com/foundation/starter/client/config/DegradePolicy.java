package com.foundation.starter.client.config;

/**
 * 只读能力的降级策略（36 号规划 ADP-08 / §6「降级语义」）。
 *
 * <p><b>默认 {@link #FAIL_CLOSED}</b>：Foundation 不可用且缓存已过期时抛出明确的
 * {@code FoundationUnavailableException}，不返回可能过期的数据。
 * {@link #STALE_CACHE} 只对配置白名单内的只读能力（字典 / 参数 / 组织 / 人员）生效，
 * 返回过期缓存并打降级标记；身份与授权类能力永远走 FAIL_CLOSED，由
 * {@link FoundationClientProperties.Degrade#policyFor(String)} 强制兜底。
 */
public enum DegradePolicy {

    /** 拒绝：缓存过期且回源失败即失败（默认，延续 S15 §11.3）。 */
    FAIL_CLOSED,

    /** 过期缓存兜底：仅限只读主数据，需审计留痕、告警与降级标记。 */
    STALE_CACHE
}
