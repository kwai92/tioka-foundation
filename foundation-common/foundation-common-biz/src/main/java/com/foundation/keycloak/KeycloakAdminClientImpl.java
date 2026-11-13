package com.foundation.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于 JDK HttpClient 的 Keycloak 管理接口客户端（{@code client_credentials} + 令牌缓存）。
 */
public class KeycloakAdminClientImpl implements KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClientImpl.class);

    /** 提前量：令牌到期前 30 秒即视为过期，避免临界失效。 */
    private static final long EXPIRY_SKEW_MILLIS = 30_000L;

    private final KeycloakProperties properties;
    private final ObjectMapper json;
    private final HttpClient httpClient;
    private final ReentrantLock tokenLock = new ReentrantLock();

    private volatile String cachedToken;
    private volatile long cachedTokenExpiresAt;

    public KeycloakAdminClientImpl(KeycloakProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
    }

    @Override
    public String realm() {
        return properties.getRealm();
    }

    @Override
    public boolean ping() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/admin/realms/" + properties.getRealm()))
                    .timeout(properties.getReadTimeout())
                    .header("Authorization", "Bearer " + accessToken())
                    .GET()
                    .build();
            int status = send(request).statusCode();
            return status == 200;
        } catch (RuntimeException e) {
            log.warn("Keycloak 管理接口连通性校验失败：{}", e.getMessage());
            return false;
        }
    }

    @Override
    public String accessToken() {
        String token = cachedToken;
        if (token != null && System.currentTimeMillis() < cachedTokenExpiresAt) {
            return token;
        }
        tokenLock.lock();
        try {
            if (cachedToken != null && System.currentTimeMillis() < cachedTokenExpiresAt) {
                return cachedToken;
            }
            HttpResponse<String> response =
                    send(tokenRequest());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Keycloak 取令牌失败：HTTP " + response.statusCode() + " " + response.body());
            }
            JsonNode body = readTree(response.body());
            String token2 = body.path("access_token").asText(null);
            if (token2 == null || token2.isBlank()) {
                throw new IllegalStateException("Keycloak 取令牌响应缺少 access_token");
            }
            long expiresInSeconds = body.path("expires_in").asLong(60L);
            cachedToken = token2;
            cachedTokenExpiresAt = System.currentTimeMillis() + expiresInSeconds * 1000L - EXPIRY_SKEW_MILLIS;
            return token2;
        } finally {
            tokenLock.unlock();
        }
    }

    private HttpRequest tokenRequest() {
        String form = "grant_type=client_credentials"
                + "&client_id=" + encode(properties.getClientId())
                + "&client_secret=" + encode(properties.getClientSecret());
        return HttpRequest.newBuilder(URI.create(baseUrl() + "/realms/" + properties.getAdminRealm()
                        + "/protocol/openid-connect/token"))
                .timeout(properties.getReadTimeout())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Keycloak 管理接口调用失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Keycloak 管理接口调用被中断", e);
        }
    }

    private JsonNode readTree(String body) {
        try {
            return json.readTree(body);
        } catch (IOException e) {
            throw new IllegalStateException("Keycloak 响应解析失败：" + e.getMessage(), e);
        }
    }

    private String baseUrl() {
        String url = properties.getServerUrl() == null ? "" : properties.getServerUrl().trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static String encode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
