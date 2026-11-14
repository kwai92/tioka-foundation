package com.foundation.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.common.api.keycloak.KeycloakLoginEvent;
import com.foundation.common.api.keycloak.KeycloakLoginEventAdmin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Keycloak Admin REST API 的登录事件读取实现（F-09 BAS-10 §登录日志）。
 *
 * <p>唯二入口：{@link KeycloakAdminClient} 取管理令牌；本类只负责事件表示，不做本地落库。
 * 事件字段缺失按 null 处理，{@code time} 缺失时以当前时刻兜底。
 */
public class KeycloakLoginEventAdminImpl implements KeycloakLoginEventAdmin {

    private final KeycloakProperties properties;
    private final KeycloakAdminClient adminClient;
    private final ObjectMapper json;
    private final HttpClient httpClient;

    public KeycloakLoginEventAdminImpl(KeycloakProperties properties, KeycloakAdminClient adminClient,
                                       ObjectMapper json) {
        this.properties = properties;
        this.adminClient = adminClient;
        this.json = json;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
    }

    @Override
    public List<KeycloakLoginEvent> listLoginEvents(Instant since, int max) {
        long dateFrom = since == null ? 0L : since.toEpochMilli();
        String path = "/admin/realms/" + realm() + "/events?type=LOGIN&type=LOGIN_ERROR&dateFrom=" + dateFrom
                + "&first=0&max=" + max;
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(properties.getReadTimeout())
                .header("Authorization", "Bearer " + adminClient.accessToken())
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Keycloak 拉取登录事件失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Keycloak 拉取登录事件被中断", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Keycloak 拉取登录事件失败：HTTP " + response.statusCode()
                    + " " + response.body());
        }
        JsonNode array = readTree(response.body());
        List<KeycloakLoginEvent> events = new ArrayList<>();
        if (!array.isArray()) {
            return events;
        }
        for (JsonNode node : array) {
            events.add(toEvent(node));
        }
        return events;
    }

    private KeycloakLoginEvent toEvent(JsonNode node) {
        JsonNode details = node.path("details");
        long time = node.path("time").asLong(0L);
        Instant occurredAt = time > 0 ? Instant.ofEpochMilli(time) : Instant.now();
        return new KeycloakLoginEvent(
                node.path("id").asText(null),
                node.path("type").asText(null),
                node.path("clientId").asText(null),
                node.path("userId").asText(null),
                details.path("username").asText(null),
                node.path("error").asText(null),
                details.path("auth_method").asText(null),
                node.path("ipAddress").asText(null),
                occurredAt);
    }

    private JsonNode readTree(String body) {
        try {
            return json.readTree(body);
        } catch (IOException e) {
            throw new IllegalStateException("Keycloak 响应解析失败：" + e.getMessage(), e);
        }
    }

    private String realm() {
        return adminClient.realm();
    }

    private String baseUrl() {
        String url = properties.getServerUrl() == null ? "" : properties.getServerUrl().trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
