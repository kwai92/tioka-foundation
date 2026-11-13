package com.foundation.common.api.error;

/** 业务异常：携带错误码，由 web 层统一翻译为 HTTP 状态与响应体（全局处理器在 BAS-24 starter-web）。 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
