package com.foundation.starter.client.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.core.context.RequestContext;
import com.foundation.starter.core.http.BearerTokenSupplier;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
        Request.Builder builder = new Request.Builder().url(baseUrl + path).get();
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

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
