package com.foundation.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foundation.common.api.keycloak.KeycloakOidcClientAdmin;
import com.foundation.common.api.keycloak.OidcClientSpec;
import com.foundation.common.api.keycloak.OidcGrantTypes;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Keycloak Admin REST API 的 OIDC 客户端实现（F-05 BAS-04）。
 *
 * <p>唯二入口：{@link KeycloakAdminClient} 取管理令牌；本类只负责客户端表示与授权类型的映射。
 * {@code grant_types} 到 Keycloak 流程开关的映射：{@code authorization_code} → {@code standardFlowEnabled}、
 * {@code implicit} → {@code implicitFlowEnabled}、{@code password} → {@code directAccessGrantsEnabled}、
 * {@code client_credentials} → {@code serviceAccountsEnabled}；{@code refresh_token} 随前两者自动可用。
 */
public class KeycloakOidcClientAdminImpl implements KeycloakOidcClientAdmin {

    private static final String PROTOCOL = "openid-connect";

    private final KeycloakProperties properties;
    private final KeycloakAdminClient adminClient;
    private final ObjectMapper json;
    private final HttpClient httpClient;

    public KeycloakOidcClientAdminImpl(KeycloakProperties properties, KeycloakAdminClient adminClient,
                                       ObjectMapper json) {
        this.properties = properties;
        this.adminClient = adminClient;
        this.json = json;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
    }

    @Override
    public void upsert(OidcClientSpec spec) {
        ObjectNode representation = representation(spec);
        JsonNode existing = findRepresentation(spec.clientId());
        if (existing == null) {
            send(request("/admin/realms/" + realm() + "/clients")
                    .POST(body(representation)).build(), "登记客户端");
            return;
        }
        // 更新：服务端既有表示（含 id 与不归本项管的字段）为本，覆盖本项负责的字段
        ObjectNode merged = ((ObjectNode) existing).deepCopy();
        merged.setAll(representation);
        send(request("/admin/realms/" + realm() + "/clients/" + existing.path("id").asText())
                .PUT(body(merged)).build(), "更新客户端");
    }

    @Override
    public String regenerateSecret(String clientId) {
        String uuid = requireUuid(clientId);
        HttpResponse<String> response = send(request("/admin/realms/" + realm() + "/clients/" + uuid + "/client-secret")
                .POST(HttpRequest.BodyPublishers.noBody()).build(), "轮换客户端密钥");
        JsonNode body = readTree(response.body());
        String secret = body.path("value").asText(null);
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Keycloak 轮换密钥响应缺少 value：" + clientId);
        }
        return secret;
    }

    @Override
    public void delete(String clientId) {
        JsonNode existing = findRepresentation(clientId);
        if (existing == null) {
            return;
        }
        send(request("/admin/realms/" + realm() + "/clients/" + existing.path("id").asText())
                .DELETE().build(), "删除客户端");
    }

    /** 按 {@code clientId} 查客户端表示；不存在返回 {@code null}。 */
    private JsonNode findRepresentation(String clientId) {
        HttpResponse<String> response = send(request("/admin/realms/" + realm() + "/clients?clientId="
                + encode(clientId)).GET().build(), "查询客户端");
        JsonNode array = readTree(response.body());
        if (!array.isArray() || array.isEmpty()) {
            return null;
        }
        return array.get(0);
    }

    /** 查询并返回客户端 UUID，缺失时抛错（调用方应在登记后调用）。 */
    private String requireUuid(String clientId) {
        JsonNode existing = findRepresentation(clientId);
        if (existing == null) {
            throw new IllegalStateException("Keycloak 中不存在客户端：" + clientId);
        }
        return existing.path("id").asText();
    }

    private ObjectNode representation(OidcClientSpec spec) {
        List<String> grants = spec.grantTypes() == null ? List.of() : spec.grantTypes();
        ObjectNode node = json.createObjectNode();
        node.put("clientId", spec.clientId());
        node.put("name", spec.name() == null || spec.name().isBlank() ? spec.clientId() : spec.name());
        node.put("enabled", true);
        node.put("protocol", PROTOCOL);
        node.put("publicClient", false);
        node.put("bearerOnly", false);
        node.put("standardFlowEnabled", grants.contains(OidcGrantTypes.AUTHORIZATION_CODE));
        node.put("implicitFlowEnabled", grants.contains(OidcGrantTypes.IMPLICIT));
        node.put("directAccessGrantsEnabled", grants.contains(OidcGrantTypes.PASSWORD));
        node.put("serviceAccountsEnabled", grants.contains(OidcGrantTypes.CLIENT_CREDENTIALS));
        ArrayNode redirects = node.putArray("redirectUris");
        if (spec.redirectUris() != null) {
            spec.redirectUris().forEach(redirects::add);
        }
        node.putArray("webOrigins").add("+");
        return node;
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
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Keycloak " + action + "失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Keycloak " + action + "被中断", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Keycloak " + action + "失败：HTTP " + response.statusCode()
                    + " " + response.body());
        }
        return response;
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
