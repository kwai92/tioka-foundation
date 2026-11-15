package com.foundation.starter.web.error;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.starter.core.context.RequestContext;
import com.foundation.starter.core.error.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理器（F-02 BAS-24 §foundation-starter-web）。
 *
 * <p>把 {@link BizException} 翻译为错误码对应的 HTTP 状态与 {@link ApiResponse}；参数类异常统一
 * 400；其余异常统一 500，仅回传错误码默认提示，绝不向响应体泄露内部堆栈信息。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 字段校验消息最大拼接条数。 */
    private static final int MAX_FIELD_MESSAGES = 5;

    /** 业务异常：HTTP 状态取错误码自带状态。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Object>> handleBizException(BizException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("业务异常 code={} message={} traceId={}", errorCode.getCode(), ex.getMessage(), traceId());
        return ResponseEntity.status(HttpStatus.valueOf(errorCode.getHttpStatus()))
                .body(ApiResponse.fail(errorCode, ex.getMessage()));
    }

    /** 请求体校验失败。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        return badRequest(fieldMessage(ex.getBindingResult().getFieldErrors()));
    }

    /** 查询参数绑定校验失败。 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Object>> handleBindException(BindException ex) {
        return badRequest(fieldMessage(ex.getBindingResult().getFieldErrors()));
    }

    /** 方法参数校验失败（Spring 6.1）。 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Object>> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        return badRequest("请求参数校验失败");
    }

    /** 缺少必需请求参数。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return badRequest("缺少必需参数：" + ex.getParameterName());
    }

    /** 请求参数类型不匹配。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest("参数类型不正确：" + ex.getName());
    }

    /** 请求体不可读（格式错误）。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return badRequest("请求体格式不正确");
    }

    /** 请求方法不支持。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("请求方法不支持 method={} traceId={}", ex.getMethod(), traceId());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.fail(ErrorCode.BAD_REQUEST, "不支持的请求方法"));
    }

    /** 兜底：未处理异常统一 500，不泄露内部细节。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception ex) {
        log.error("未处理异常 traceId={}", traceId(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorCode.INTERNAL, ErrorCode.INTERNAL.getMessage()));
    }

    private ResponseEntity<ApiResponse<Object>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCode.BAD_REQUEST, message));
    }

    private String fieldMessage(List<FieldError> fieldErrors) {
        String joined = fieldErrors.stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .limit(MAX_FIELD_MESSAGES)
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return joined.isBlank() ? ErrorCode.BAD_REQUEST.getMessage() : joined;
    }

    private String traceId() {
        return RequestContext.traceId().orElse("-");
    }
}
