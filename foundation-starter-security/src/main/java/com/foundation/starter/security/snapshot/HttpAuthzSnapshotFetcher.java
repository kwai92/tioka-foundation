package com.foundation.starter.security.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.core.contract.FoundationApiVersion;
import com.foundation.starter.core.http.BearerTokenSupplier;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;

/**
 * 经 HTTP 从 Foundation 取快照（F-07 §4、§11）。
 *
 * <p>调用清单：{@code GET /api/fdn/apps/{appCode}/persons/{personId}/snapshot[/version]}；
 * 出站带 {@code Authorization}（服务账号令牌，由 {@link BearerTokenSupplier} 提供）、
 * {@link FoundationApiVersion#HEADER} 契约版本头与 {@code traceparent} / {@code request_id}
 * 透传（F-07 §11、ADP-03）。网络失败一律抛异常，
 * 降级策略由 {@link SnapshotCache} 决定。
 */
public class HttpAuthzSnapshotFetcher implements AuthzSnapshotFetcher {

    private static final String SNAPSHOT_PATH = "/api/fdn/apps/%s/persons/%s/snapshot";

    private final OkHttpClient http;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final BearerTokenSupplier tokenSupplier;

    public HttpAuthzSnapshotFetcher(OkHttpClient http,
                                   ObjectMapper objectMapper,
                                   String baseUrl,
                                   BearerTokenSupplier tokenSupplier) {
        this.http = http;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.tokenSupplier = tokenSupplier;
    }

    @Override
    public AuthzSnapshot load(String appCode, String personId) {
        String body = get(snapshotUrl(appCode, personId));
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, AuthzSnapshot.class);
        } catch (IOException ex) {
            throw new IllegalStateException("解析授权快照失败：" + ex.getMessage(), ex);
        }
    }

    @Override
    public Long version(String appCode, String personId) {
        String body = get(snapshotUrl(appCode, personId) + "/version");
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            var node = objectMapper.readTree(body);
            return node.isNumber() ? node.asLong() : node.path("version").asLong();
        } catch (IOException ex) {
            throw new IllegalStateException("解析授权快照版本失败：" + ex.getMessage(), ex);
        }
    }

    private String snapshotUrl(String appCode, String personId) {
        return baseUrl + String.format(SNAPSHOT_PATH, appCode, personId);
    }

    private String get(String url) {
        Request.Builder builder = new Request.Builder().url(url).get()
                .header(FoundationApiVersion.HEADER, FoundationApiVersion.CURRENT);
        String token = tokenSupplier == null ? null : tokenSupplier.bearerToken();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        var traceId = com.foundation.starter.core.context.RequestContext.traceId();
        traceId.ifPresent(id -> {
            builder.header("request_id", id);
            builder.header("traceparent", "00-" + id + "-0000000000000000-01");
        });
        try (Response response = http.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("取快照失败：HTTP " + response.code());
            }
            ResponseBody body = response.body();
            return body == null ? null : body.string();
        } catch (IOException ex) {
            throw new IllegalStateException("取快照失败：" + ex.getMessage(), ex);
        }
    }
}
