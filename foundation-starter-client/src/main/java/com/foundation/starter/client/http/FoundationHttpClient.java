package com.foundation.starter.client.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.core.contract.FoundationApiVersion;
import com.foundation.starter.core.context.RequestContext;
import com.foundation.starter.core.http.BearerTokenSupplier;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Foundation 出站客户端（F-07 §11）：超时、指数退避 + 抖动重试、轻量熔断、trace 透传。
 *
 * <p>所有调用均为**只读接口**，且**不在请求线程同步发生**的热路径上（快照 / 字典 / 组织均有本地缓存）。
 */
public class FoundationHttpClient {

    private static final Logger log = LoggerFactory.getLogger(FoundationHttpClient.class);

    private final OkHttpClient http;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final BearerTokenSupplier tokenSupplier;
    private final CircuitBreaker circuitBreaker;
    private final int maxAttempts;
    private final long baseDelayMillis;

    public FoundationHttpClient(OkHttpClient http,
                                ObjectMapper objectMapper,
                                String baseUrl,
                                BearerTokenSupplier tokenSupplier,
                                CircuitBreaker circuitBreaker,
                                int maxAttempts,
                                long baseDelayMillis) {
        this.http = http;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.tokenSupplier = tokenSupplier;
        this.circuitBreaker = circuitBreaker;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseDelayMillis = Math.max(1L, baseDelayMillis);
    }

    /** GET 并解析为 JSON；空响应返回 {@code null}。 */
    public JsonNode get(String path) {
        if (!circuitBreaker.allow()) {
            throw new IllegalStateException("Foundation 客户端熔断中：" + path);
        }
        RuntimeException last = null;
        long delayMillis = baseDelayMillis;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                JsonNode node = parse(execute(path));
                circuitBreaker.recordSuccess();
                return node;
            } catch (RuntimeException ex) {
                last = ex;
                circuitBreaker.recordFailure();
                if (attempt == maxAttempts) {
                    break;
                }
                sleep(delayMillis + ThreadLocalRandom.current().nextLong(delayMillis + 1));
                delayMillis *= 2;
            }
        }
        log.warn("调用 Foundation 失败（已重试 {} 次）：{}", maxAttempts, path);
        throw last == null ? new IllegalStateException("调用 Foundation 失败：" + path) : last;
    }

    /** 原始字节 GET（流式下载用，不解析 JSON）。 */
    public byte[] getBytes(String path) {
        if (!circuitBreaker.allow()) {
            throw new IllegalStateException("Foundation 客户端熔断中：" + path);
        }
        Request.Builder builder = authorized(new Request.Builder().url(baseUrl + path).get());
        try (Response response = http.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("调用 Foundation 失败：HTTP " + response.code() + " " + path);
            }
            ResponseBody body = response.body();
            byte[] bytes = body == null ? new byte[0] : body.bytes();
            circuitBreaker.recordSuccess();
            return bytes;
        } catch (IOException ex) {
            circuitBreaker.recordFailure();
            throw new IllegalStateException("调用 Foundation 失败：" + path + "（" + ex.getMessage() + "）", ex);
        }
    }

    /**
     * {@code multipart/form-data} 上传（写接口，**非幂等**，不带重试）；返回响应 JSON。
     *
     * @param fieldName 文件字段名（如 {@code file}）
     * @param fields    附带表单字段（可空）
     */
    public JsonNode postMultipart(String path, String fieldName, String fileName, String contentType,
                                  byte[] content, Map<String, String> fields) {
        if (!circuitBreaker.allow()) {
            throw new IllegalStateException("Foundation 客户端熔断中：" + path);
        }
        MultipartBody.Builder form = new MultipartBody.Builder().setType(MultipartBody.FORM);
        if (fields != null) {
            fields.forEach(form::addFormDataPart);
        }
        MediaType partType = MediaType.parse(contentType == null || contentType.isBlank()
                ? "application/octet-stream" : contentType);
        form.addFormDataPart(fieldName, fileName,
                RequestBody.create(content == null ? new byte[0] : content, partType));
        Request.Builder builder = authorized(new Request.Builder().url(baseUrl + path)).post(form.build());
        try {
            JsonNode node = parse(send(builder, path));
            circuitBreaker.recordSuccess();
            return node;
        } catch (RuntimeException ex) {
            circuitBreaker.recordFailure();
            throw ex;
        }
    }

    /**
     * JSON POST（写接口，**非幂等**，不带重试；幂等性由业务头保证，如 {@code Idempotency-Key}）。
     *
     * @param path    相对路径
     * @param body    请求体（序列化为 JSON；{@code null} 视为空对象）
     * @param headers 附加请求头（可空）
     * @return 响应 JSON；空响应返回 {@code null}
     */
    public JsonNode postJson(String path, Object body, Map<String, String> headers) {
        if (!circuitBreaker.allow()) {
            throw new IllegalStateException("Foundation 客户端熔断中：" + path);
        }
        RequestBody requestBody;
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body == null ? Map.of() : body);
            requestBody = RequestBody.create(bytes, MediaType.parse("application/json; charset=utf-8"));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化请求体失败：" + ex.getMessage(), ex);
        }
        Request.Builder builder = authorized(new Request.Builder().url(baseUrl + path)).post(requestBody);
        if (headers != null) {
            headers.forEach(builder::header);
        }
        try {
            JsonNode node = parse(send(builder, path));
            circuitBreaker.recordSuccess();
            return node;
        } catch (RuntimeException ex) {
            circuitBreaker.recordFailure();
            throw ex;
        }
    }

    /** DELETE（写接口，**非幂等**，不带重试）。 */
    public void delete(String path) {
        if (!circuitBreaker.allow()) {
            throw new IllegalStateException("Foundation 客户端熔断中：" + path);
        }
        Request.Builder builder = authorized(new Request.Builder().url(baseUrl + path)).delete();
        try {
            send(builder, path);
            circuitBreaker.recordSuccess();
        } catch (RuntimeException ex) {
            circuitBreaker.recordFailure();
            throw ex;
        }
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (IOException ex) {
            throw new IllegalStateException("解析 Foundation 响应失败：" + ex.getMessage(), ex);
        }
    }

    private String execute(String path) {
        Request.Builder builder = new Request.Builder().url(baseUrl + path).get()
                .header(FoundationApiVersion.HEADER, FoundationApiVersion.CURRENT);
        String token = tokenSupplier == null ? null : tokenSupplier.bearerToken();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        RequestContext.traceId().ifPresent(traceId -> {
            builder.header("request_id", traceId);
            builder.header("traceparent", "00-" + traceId + "-0000000000000000-01");
        });
        try (Response response = http.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("调用 Foundation 失败：HTTP " + response.code() + " " + path);
            }
            ResponseBody body = response.body();
            return body == null ? null : body.string();
        } catch (IOException ex) {
            throw new IllegalStateException("调用 Foundation 失败：" + path + "（" + ex.getMessage() + "）", ex);
        }
    }

    /** 附加认证与 trace 透传头。 */
    private Request.Builder authorized(Request.Builder builder) {
        builder.header(FoundationApiVersion.HEADER, FoundationApiVersion.CURRENT);
        String token = tokenSupplier == null ? null : tokenSupplier.bearerToken();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        RequestContext.traceId().ifPresent(traceId -> {
            builder.header("request_id", traceId);
            builder.header("traceparent", "00-" + traceId + "-0000000000000000-01");
        });
        return builder;
    }

    /** 执行请求并取响应体文本；非 2xx 抛 {@link IllegalStateException}。 */
    private String send(Request.Builder builder, String path) {
        try (Response response = http.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("调用 Foundation 失败：HTTP " + response.code() + " " + path);
            }
            ResponseBody body = response.body();
            return body == null ? null : body.string();
        } catch (IOException ex) {
            throw new IllegalStateException("调用 Foundation 失败：" + path + "（" + ex.getMessage() + "）", ex);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
