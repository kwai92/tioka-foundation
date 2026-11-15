package com.foundation.starter.client.http;

/**
 * Foundation 不可用且无可用兜底数据时的明确异常（36 号规划 ADP-08）。
 *
 * <p>用于替代「熔断中直接抛 {@code IllegalStateException}」「HTTP 客户端静默返回 null」
 * 这类让业务方无法区分「服务故障」与「业务空结果」的行为。异常携带资源标识与原始原因，
 * 便于网关映射为 503、便于业务方决定是否启用降级。
 */
public class FoundationUnavailableException extends RuntimeException {

    private final String resource;

    public FoundationUnavailableException(String resource, String reason) {
        this(resource, reason, null);
    }

    public FoundationUnavailableException(String resource, String reason, Throwable cause) {
        super("Foundation 不可用：" + resource + "（" + reason + "）", cause);
        this.resource = resource;
    }

    /** 未能取到的资源标识（缓存键或请求路径）。 */
    public String getResource() {
        return resource;
    }
}
