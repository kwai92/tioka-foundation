package com.foundation.starter.client.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.core.config.FoundationProperties;
import com.foundation.starter.core.http.BearerTokenSupplier;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 服务账号令牌（F-07 §11）：应用登记时由 Foundation 自动开通（BAS-04，{@code client_credentials}）。
 *
 * <p>starter 用服务账号换 access token，**缓存至过期前**（提前 30 秒刷新）；凭据只写不读，
 * 经环境变量注入。未配置令牌端点时 {@link #bearerToken()} 返回 {@code null}（出站不带 Authorization）。
 */
public class ServiceAccountTokenProvider implements BearerTokenSupplier {

    private static final Logger log = LoggerFactory.getLogger(ServiceAccountTokenProvider.class);
    private static final long REFRESH_AHEAD_MILLIS = 30_000L;

    private final OkHttpClient http;
    private final ObjectMapper objectMapper;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;

    private volatile String accessToken;
    private volatile long expiresAtMillis;

    public ServiceAccountTokenProvider(OkHttpClient http,
                                       ObjectMapper objectMapper,
                                       FoundationProperties.ServiceAccount serviceAccount) {
        this.http = http;
        this.objectMapper = objectMapper;
        this.tokenUri = serviceAccount == null ? null : serviceAccount.getTokenUri();
        this.clientId = serviceAccount == null ? null : serviceAccount.getClientId();
        this.clientSecret = serviceAccount == null ? null : serviceAccount.getClientSecret();
    }

    @Override
    public String bearerToken() {
        if (tokenUri == null || tokenUri.isBlank() || clientId == null || clientId.isBlank()) {
            return null;
        }
        if (accessToken != null && System.currentTimeMillis() < expiresAtMillis - REFRESH_AHEAD_MILLIS) {
            return accessToken;
        }
        return refresh();
    }

    /** 立即刷新令牌；失败返回当前（可能已过期的）令牌或 {@code null}。 */
    public synchronized String refresh() {
        if (accessToken != null && System.currentTimeMillis() < expiresAtMillis - REFRESH_AHEAD_MILLIS) {
            return accessToken;
        }
        FormBody form = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", clientId)
                .add("client_secret", clientSecret == null ? "" : clientSecret)
                .build();
        Request request = new Request.Builder().url(tokenUri).post(form).build();
        try (Response response = http.newCall(request).execute()) {
            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();
            if (!response.isSuccessful()) {
                log.warn("服务账号换令牌失败：HTTP {} {}", response.code(), text);
                return accessToken;
            }
            JsonNode node = objectMapper.readTree(text);
            this.accessToken = node.path("access_token").asText(null);
            long expiresIn = node.path("expires_in").asLong(300L);
            this.expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000L;
            return accessToken;
        } catch (IOException ex) {
            log.warn("服务账号换令牌异常：{}", ex.getMessage());
            return accessToken;
        }
    }
}
