package com.foundation.common.api.error;

/**
 * Foundation 错误码（F-00 §5 约定：code 5 位、HTTP 状态对齐）。
 *
 * <p>BAS-24 抽取 {@code foundation-starter-core} 后，本枚举随之收敛；在此之前由公共域自持。
 */
public enum ErrorCode {

    BAD_REQUEST(40000, 400, "请求参数错误"),
    UNAUTHORIZED(40100, 401, "未登录或令牌无效"),
    FORBIDDEN(40300, 403, "无权限"),
    NOT_FOUND(40400, 404, "资源不存在"),
    CONFLICT(40900, 409, "资源冲突"),
    INTERNAL(50000, 500, "内部错误");

    private final int code;
    private final int httpStatus;
    private final String message;

    ErrorCode(int code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
