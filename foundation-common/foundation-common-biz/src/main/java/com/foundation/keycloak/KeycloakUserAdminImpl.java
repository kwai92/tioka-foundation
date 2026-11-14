package com.foundation.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foundation.common.api.keycloak.KeycloakSessionView;
import com.foundation.common.api.keycloak.KeycloakUserAdmin;
import com.foundation.common.api.keycloak.KeycloakUserSpec;
import com.foundation.common.api.keycloak.KeycloakUserView;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Keycloak Admin REST API 的用户实现（F-04 BAS-03）。
 *
 * <p>唯二入口：{@link KeycloakAdminClient} 取管理令牌；本类只负责用户表示与生命周期动作。
 * 所有写操作先落 Foundation 库、再经本类推送 Keycloak（F-04 §5）。
 * 明文口令只出现在 {@link #resetPassword} 的入参中，不回读、不落库、不进日志。
 */
public class KeycloakUserAdminImpl implements KeycloakUserAdmin {

    private static final int LIST_PAGE_SIZE = 100;

    private final KeycloakProperties properties;
    private final KeycloakAdminClient adminClient;
    private final ObjectMapper json;
    private final HttpClient httpClient;

    public KeycloakUserAdminImpl(KeycloakProperties properties, KeycloakAdminClient adminClient, ObjectMapper json) {
        this.properties = properties;
        this.adminClient = adminClient;
        this.json = json;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
    }

    @Override
    public String createUser(KeycloakUserSpec spec) {
        KeycloakUserView existing = findByUsername(spec.username());
        if (existing != null) {
            return existing.id();
        }
        HttpResponse<String> response = send(request("/admin/realms/" + realm() + "/users")
                .POST(body(representation(spec))).build(), "创建用户");
        return requireCreatedId(response, spec.username());
    }

    @Override
    public KeycloakUserView findUser(String userId) {
        HttpResponse<String> response = send(request("/admin/realms/" + realm() + "/users/" + encode(userId))
                .GET().build(), "查询用户", true);
        if (response.statusCode() == 404) {
            return null;
        }
        requireSuccess(response, "查询用户");
        return toView(readTree(response.body()));
    }

    @Override
    public KeycloakUserView findByUsername(String username) {
        HttpResponse<String> response = send(request("/admin/realms/" + realm() + "/users?username="
                + encode(username) + "&exact=true").GET().build(), "按登录名查询用户");
        JsonNode array = readTree(response.body());
        if (!array.isArray() || array.isEmpty()) {
            return null;
        }
        return toView(array.get(0));
    }

    @Override
    public List<KeycloakUserView> listUsers() {
        List<KeycloakUserView> users = new ArrayList<>();
        int first = 0;
        while (true) {
            HttpResponse<String> response = send(request("/admin/realms/" + realm() + "/users?first=" + first
                    + "&max=" + LIST_PAGE_SIZE).GET().build(), "列出用户");
            JsonNode array = readTree(response.body());
            if (!array.isArray() || array.isEmpty()) {
                return users;
            }
            array.forEach(node -> users.add(toView(node)));
            if (array.size() < LIST_PAGE_SIZE) {
                return users;
            }
            first += LIST_PAGE_SIZE;
        }
    }

    @Override
    public void setEnabled(String userId, boolean enabled) {
        ObjectNode patch = json.createObjectNode();
        patch.put("enabled", enabled);
        putUser(userId, patch, "设置用户启停");
    }

    @Override
    public void setUsername(String userId, String username) {
        ObjectNode patch = json.createObjectNode();
        patch.put("username", username);
        putUser(userId, patch, "修改登录名");
    }

    @Override
    public void setRequiredActions(String userId, List<String> requiredActions) {
        ObjectNode patch = json.createObjectNode();
        ArrayNode actions = patch.putArray("requiredActions");
        if (requiredActions != null) {
            requiredActions.forEach(actions::add);
        }
        putUser(userId, patch, "设置必需动作");
    }

    @Override
    public void deleteUser(String userId) {
        send(request("/admin/realms/" + realm() + "/users/" + encode(userId))
                .DELETE().build(), "删除用户", true);
    }

    @Override
    public void resetPassword(String userId, String temporaryPassword, boolean temporary) {
        ObjectNode payload = json.createObjectNode();
        payload.put("type", "password");
        payload.put("value", temporaryPassword);
        payload.put("temporary", temporary);
        send(request("/admin/realms/" + realm() + "/users/" + encode(userId) + "/reset-password")
                .PUT(body(payload)).build(), "重置口令");
    }

    @Override
    public void sendPasswordResetEmail(String userId) {
        ArrayNode actions = json.createArrayNode().add("UPDATE_PASSWORD");
        send(request("/admin/realms/" + realm() + "/users/" + encode(userId) + "/execute-actions-email")
                .PUT(body(actions)).build(), "发送重置口令邮件");
    }

    @Override
    public void logoutSessions(String userId) {
        send(request("/admin/realms/" + realm() + "/users/" + encode(userId) + "/logout")
                .POST(HttpRequest.BodyPublishers.noBody()).build(), "注销用户会话");
    }

    @Override
    public List<KeycloakSessionView> listSessions(String userId) {
        HttpResponse<String> response = send(request("/admin/realms/" + realm() + "/users/" + encode(userId)
                + "/sessions").GET().build(), "查询用户会话");
        JsonNode array = readTree(response.body());
        List<KeycloakSessionView> sessions = new ArrayList<>();
        if (!array.isArray()) {
            return sessions;
        }
        for (JsonNode node : array) {
            sessions.add(new KeycloakSessionView(
                    node.path("id").asText(null),
                    node.path("userId").asText(userId),
                    node.path("username").asText(null),
                    node.path("ipAddress").asText(null),
                    node.path("start").asLong(0L),
                    node.path("lastAccess").asLong(0L)));
        }
        return sessions;
    }

    private void putUser(String userId, ObjectNode patch, String action) {
        send(request("/admin/realms/" + realm() + "/users/" + encode(userId))
                .PUT(body(patch)).build(), action);
    }

    private String requireCreatedId(HttpResponse<String> response, String username) {
        String location = response.headers().firstValue("Location").orElse(null);
        if (location != null) {
            int idx = location.lastIndexOf('/');
            if (idx >= 0 && idx < location.length() - 1) {
                return location.substring(idx + 1);
            }
        }
        // 少数实现不回 Location：按登录名回查兜底
        KeycloakUserView created = findByUsername(username);
        if (created == null) {
            throw new IllegalStateException("Keycloak 创建用户成功但无法解析用户 ID：" + username);
        }
        return created.id();
    }

    private ObjectNode representation(KeycloakUserSpec spec) {
        ObjectNode node = json.createObjectNode();
        node.put("username", spec.username());
        node.put("enabled", spec.enabled());
        if (spec.email() != null && !spec.email().isBlank()) {
            node.put("email", spec.email());
        }
        if (spec.firstName() != null && !spec.firstName().isBlank()) {
            node.put("firstName", spec.firstName());
        }
        if (spec.lastName() != null && !spec.lastName().isBlank()) {
            node.put("lastName", spec.lastName());
        }
        ArrayNode actions = node.putArray("requiredActions");
        if (spec.requiredActions() != null) {
            spec.requiredActions().forEach(actions::add);
        }
        return node;
    }

    private KeycloakUserView toView(JsonNode node) {
        List<String> actions = new ArrayList<>();
        JsonNode actionsNode = node.path("requiredActions");
        if (actionsNode.isArray()) {
            actionsNode.forEach(action -> actions.add(action.asText()));
        }
        return new KeycloakUserView(
                node.path("id").asText(null),
                node.path("username").asText(null),
                node.path("email").asText(null),
                node.path("enabled").asBoolean(true),
                actions);
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
        return send(request, action, false);
    }

    private HttpResponse<String> send(HttpRequest request, String action, boolean allowNotFound) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Keycloak " + action + "失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Keycloak " + action + "被中断", e);
        }
        if (allowNotFound && response.statusCode() == 404) {
            return response;
        }
        requireSuccess(response, action);
        return response;
    }

    private void requireSuccess(HttpResponse<String> response, String action) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Keycloak " + action + "失败：HTTP " + response.statusCode()
                    + " " + response.body());
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
