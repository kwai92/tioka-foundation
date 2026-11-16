package com.foundation.starter.client.http;

/**
 * 响应回写的主版本与请求不一致（ADP-03）。
 *
 * <p>典型的并行发布期串版本：网关把 v1 请求路由到 v2 实例。这类错误是确定性契约错误，
 * 不是上游抖动，不能计入重试与熔断统计，必须立即抛出让业务方按版本错配处理。
 */
public class FoundationApiVersionMismatchException extends RuntimeException {

    private final String path;
    private final String echoedMajor;

    public FoundationApiVersionMismatchException(String path, String echoedMajor, int requestedMajor) {
        super("Foundation API 版本错配：" + path + " 响应回写 " + echoedMajor
                + "，当前客户端只支持主版本 " + requestedMajor);
        this.path = path;
        this.echoedMajor = echoedMajor;
    }

    public String getPath() {
        return path;
    }

    public String getEchoedMajor() {
        return echoedMajor;
    }
}
