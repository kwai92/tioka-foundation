package com.foundation.starter.client.http;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/**
 * Foundation 不可用且无可用兜底数据时的明确异常（36 号规划 ADP-08）。
 *
 * <p>用于替代「熔断中直接抛 {@code IllegalStateException}」「HTTP 客户端静默返回 null」
 * 这类让业务方无法区分「服务故障」与「业务空结果」的行为。异常携带资源标识与原始原因，
 * 便于业务方决定是否启用降级。
 *
 * <p>继承 {@link BizException} 并携带 {@link ErrorCode#SERVICE_UNAVAILABLE}：
 * {@code foundation-starter-web} 的全局异常处理器据此返回 HTTP 503（而不是把上游故障
 * 误报成服务端内部错误 500），网关才能正确熔断/重试。
 */
public class FoundationUnavailableException extends BizException {

    private final String resource;

    public FoundationUnavailableException(String resource, String reason) {
        this(resource, reason, null);
    }

    public FoundationUnavailableException(String resource, String reason, Throwable cause) {
        super(ErrorCode.SERVICE_UNAVAILABLE,
                "Foundation 不可用：" + resource + "（" + reason + "）");
        this.resource = resource;
        if (cause != null) {
            initCause(cause);
        }
    }

    /** 未能取到的资源标识（缓存键或请求路径）。 */
    public String getResource() {
        return resource;
    }
}
