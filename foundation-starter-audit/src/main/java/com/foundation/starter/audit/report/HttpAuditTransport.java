package com.foundation.starter.audit.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.common.id.Ids;
import com.foundation.starter.core.context.RequestContext;
import com.foundation.starter.core.http.BearerTokenSupplier;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 审计投递的 HTTP 实现（F-07 §6、§11）：{@code POST /api/fdn/audit/events:batch}。
 *
 * <p>带 {@code Idempotency-Key}（S15 §14.1）与 {@code request_id} / {@code traceparent} 透传；
 * 非 2xx 抛异常，由 {@link AsyncBatchAuditReporter} 重试并最终落盘。
 */
public class HttpAuditTransport implements AuditTransport {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String PATH = "/api/fdn/audit/events:batch";

    private final OkHttpClient http;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final BearerTokenSupplier tokenSupplier;

    public HttpAuditTransport(OkHttpClient http,
                              ObjectMapper objectMapper,
                              String baseUrl,
                              BearerTokenSupplier tokenSupplier) {
        this.http = http;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.tokenSupplier = tokenSupplier;
    }

    @Override
    public void send(List<AuditEvent> batch) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(Map.of("events", batch));
        } catch (IOException ex) {
            throw new IllegalStateException("序列化审计事件失败：" + ex.getMessage(), ex);
        }
        Request.Builder builder = new Request.Builder()
                .url(baseUrl + PATH)
                .post(RequestBody.create(body, JSON))
                .header("Idempotency-Key", Ids.newId("idem"));
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
                ResponseBody responseBody = response.body();
                String hint = responseBody == null ? "" : responseBody.string();
                throw new IllegalStateException("审计上报失败：HTTP " + response.code() + " " + hint);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("审计上报失败：" + ex.getMessage(), ex);
        }
    }
}
