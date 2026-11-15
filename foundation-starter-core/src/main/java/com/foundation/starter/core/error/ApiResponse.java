package com.foundation.starter.core.error;

import com.foundation.common.api.error.ErrorCode;
import com.foundation.starter.core.context.RequestContext;

/**
 * 统一响应体（00-施工总纲 §2.3：{@code ApiResponse{code,message,data,traceId}}）。
 *
 * <p>成功码 {@code 0}；失败码取 {@link ErrorCode#getCode()}，HTTP 状态取
 * {@link ErrorCode#getHttpStatus()}（由 {@code foundation-starter-web} 的全局异常处理器翻译）。
 * {@code traceId} 取自 {@link RequestContext}，请求边界之外为空。
 *
 * @param <T> 业务数据类型
 */
public class ApiResponse<T> {

    /** 成功码（沿用工装约定 {@code code=0}）。 */
    public static final int SUCCESS_CODE = 0;

    /** 成功提示。 */
    public static final String SUCCESS_MESSAGE = "成功";

    private int code;
    private String message;
    private T data;
    private String traceId;

    /** 成功响应（无数据）。 */
    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    /** 成功响应。 */
    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = SUCCESS_CODE;
        response.message = SUCCESS_MESSAGE;
        response.data = data;
        response.traceId = RequestContext.traceId().orElse(null);
        return response;
    }

    /** 失败响应（{@code message} 为空时取错误码默认提示）。 */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return fail(errorCode, message, null);
    }

    /** 失败响应。 */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = errorCode.getCode();
        response.message = message == null ? errorCode.getMessage() : message;
        response.data = data;
        response.traceId = RequestContext.traceId().orElse(null);
        return response;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String getTraceId() {
        return traceId;
    }
}
