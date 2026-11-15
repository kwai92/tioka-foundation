package com.foundation.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foundation.common.api.keycloak.KeycloakRealmAdmin;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Keycloak Admin REST API 的 realm 级配置下发实现（F-04 BAS-15）。
 *
 * <p>唯二入口：{@link KeycloakAdminClient} 取管理令牌；本类只负责 realm 表示与会话 / 密码 / 必需动作字段的映射。
 * 会话策略映射：{@code ssoSessionIdleTimeout}（空闲，秒）、{@code ssoSessionMaxLifespan}（最长，秒）；
 * 同账号并发会话上限写入 realm attributes {@code foundation.session.concurrent}（Keycloak 无同名字段，
 * 由网关 / 认证流程按该属性校验）。登录方式以身份代理（Identity Provider）别名为 {@code modeCode} 定位。
 */
public class KeycloakRealmAdminImpl implements KeycloakRealmAdmin {

    /** 并发会话上限写入 realm attributes 的键（Keycloak 无内建同名 realm 字段）。 */
    public static final String CONCURRENT_SESSIONS_ATTR = "foundation.session.concurrent";

    private final KeycloakProperties properties;
    private final KeycloakAdminClient adminClient;
    private final ObjectMapper json;
    private final HttpClient httpClient;

    public KeycloakRealmAdminImpl(KeycloakProperties properties, KeycloakAdminClient adminClient,
                                  ObjectMapper json) {
        this.properties = properties;
        this.adminClient = adminClient;
        this.json = json;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
    }

    @Override
    public void applyPasswordPolicy(String passwordPolicy) {
        ObjectNode realm = currentRealm();
        realm.put("passwordPolicy", passwordPolicy == null ? "" : passwordPolicy);
        updateRealm(realm);
    }

    @Override
    public void applySessionPolicy(int idleMinutes, int maxHours, int concurrentSessions) {
        ObjectNode realm = currentRealm();
        realm.put("ssoSessionIdleTimeout", Math.max(0, idleMinutes) * 60);
        realm.put("ssoSessionMaxLifespan", Math.max(0, maxHours) * 3600);
        ObjectNode attributes = realm.has("attributes") && realm.get("attributes").isObject()
                ? (ObjectNode) realm.get("attributes")
                : realm.putObject("attributes");
        attributes.put(CONCURRENT_SESSIONS_ATTR, String.valueOf(Math.max(0, concurrentSessions)));
        updateRealm(realm);
    }

    @Override
    public void applyLoginMode(String modeCode, boolean enabled) {
        JsonNode idp = findIdentityProvider(modeCode);
        if (idp == null) {
            throw new IllegalStateException("Keycloak 中不存在身份代理：" + modeCode);
        }
        ObjectNode merged = ((ObjectNode) idp).deepCopy();
        merged.put("enabled", enabled);
        send(request("/admin/realms/" + realm() + "/identity-provider/instances/"
                + merged.path("alias").asText()).PUT(body(merged)).build(), "下发登录方式：" + modeCode);
    }

    @Override
    public void applyRequiredActions(List<String> requiredActions) {
        List<String> aliases = requiredActions == null ? List.of() : requiredActions;
        ObjectNode realm = currentRealm();
        ArrayNode actions = realm.has("requiredActions") && realm.get("requiredActions").isArray()
                ? (ArrayNode) realm.get("requiredActions")
                : realm.putArray("requiredActions");
        for (String alias : aliases) {
            ObjectNode action = findAction(actions, alias);
            if (action == null) {
                action = json.createObjectNode();
                action.put("alias", alias);
                actions.add(action);
            }
            action.put("enabled", true);
            action.put("defaultAction", true);
        }
        updateRealm(realm);
    }

    /** 取 realm 当前表示。 */
    private ObjectNode currentRealm() {
        HttpResponse<String> response = send(request("/admin/realms/" + realm()).GET().build(), "读取 realm");
        JsonNode body = readTree(response.body());
        if (!body.isObject()) {
            throw new IllegalStateException("Keycloak 读取 realm 响应非法：" + response.body());
        }
        return ((ObjectNode) body).deepCopy();
    }

    /** 以当前表示为底更新 realm。 */
    private void updateRealm(ObjectNode realm) {
        send(request("/admin/realms/" + realm()).PUT(body(realm)).build(), "更新 realm");
    }

    /** 按别名查身份代理表示；不存在（404）返回 {@code null}。 */
    private JsonNode findIdentityProvider(String alias) {
        HttpResponse<String> response = sendRaw(request("/admin/realms/" + realm()
                + "/identity-provider/instances/" + encode(alias)).GET().build());
        if (response.statusCode() == 404) {
            return null;
        }
        requireSuccess(response, "查询登录方式");
        JsonNode body = readTree(response.body());
        return body.isObject() ? body : null;
    }

    private static ObjectNode findAction(ArrayNode actions, String alias) {
        for (JsonNode node : actions) {
            if (node.isObject() && alias.equals(node.path("alias").asText(null))) {
                return (ObjectNode) node;
            }
        }
        return null;
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(properties.getReadTimeout())
                .header("Authorization", "Bearer " + adminClient.accessToken())
                .header("Content-Type", "application/json");
    }

    private static HttpRequest.BodyPublisher body(JsonNode node) {
        return HttpRequest.BodyPublishers.ofString(node.toString(), StandardCharsets.UTF_8);
    }

    private HttpResponse<String> send(HttpRequest request, String action) {
        HttpResponse<String> response = sendRaw(request);
        requireSuccess(response, action);
        return response;
    }

    private static void requireSuccess(HttpResponse<String> response, String action) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Keycloak " + action + "失败：HTTP " + response.statusCode()
                    + " " + response.body());
        }
    }

    private HttpResponse<String> sendRaw(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Keycloak 管理接口调用失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Keycloak 管理接口调用被中断", e);
        }
    }

    private JsonNode readTree(String bodyNode) {
        try {
            return json.readTree(bodyNode);
        } catch (IOException e) {
            throw new IllegalStateException("Keycloak 响应解析失败：" + e.getMessage(), e);
        }
    }

    private String realm() {
        return properties.getRealm();
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
