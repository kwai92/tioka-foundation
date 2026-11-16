package com.foundation.starter.client.authz;

import com.fasterxml.jackson.databind.JsonNode;
import com.foundation.starter.client.http.FoundationHttpClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 经 {@link FoundationHttpClient} 读取中台授权快照，只把原始权限项交出去。
 *
 * <p>响应结构与 Foundation 服务端 {@code AuthzSnapshotView} 对齐。这里刻意只解析
 * {@code personId} / {@code appCode} / {@code version} / {@code permissions} 四个字段：
 * 权限码的语义转换属于接入方（例如 Tioka 的 {@code PermissionCodeMapper}），
 * 底座不得替业务方解释权限含义，也不得把中台通配码直接灌给接入方。
 *
 * <p><b>严格校验（fail-closed）</b>：响应不是对象、身份标识与请求不一致、
 * {@code version} 不是非负整数、{@code permissions} 缺失或不是数组、数组含非文本项，
 * 一律返回 {@code null}；由调用方按「数据源不可用」拒绝放行。返回空集合只表示
 * 中台明确授予零个权限项。
 *
 * <p>P1：{@link #snapshotFor(String)} 保留版本号供平台版本化缓存使用；
 * {@link #permissionsFor(String)} 是它的窄视图，既有接入方零改动。
 */
public final class HttpAuthorizationSnapshotClient implements AuthorizationSnapshotClient {

    private static final String SNAPSHOT_PATH = "/api/fdn/apps/%s/persons/%s/snapshot";

    /** 与 {@code FoundationProperties#appCode} 的登记正则一致，避免拼进路径时发生注入。 */
    private static final String APP_CODE_PATTERN = "^[a-z][a-z0-9-]{1,31}$";

    private final FoundationHttpClient http;
    private final String appCode;

    public HttpAuthorizationSnapshotClient(FoundationHttpClient http, String appCode) {
        if (http == null) {
            throw new IllegalArgumentException("FoundationHttpClient is required");
        }
        if (appCode == null || !appCode.matches(APP_CODE_PATTERN)) {
            throw new IllegalArgumentException("foundation.app-code must match " + APP_CODE_PATTERN);
        }
        this.http = http;
        this.appCode = appCode;
    }

    /** 本客户端绑定的应用编码；事件失效与应用隔离都以此为边界。 */
    public String appCode() {
        return appCode;
    }

    /**
     * 读取完整平台视图（含版本号）。任何结构异常都返回 {@code null}，绝不返回部分构造的快照。
     */
    public AuthorizationSnapshot snapshotFor(String personId) {
        if (personId == null || personId.isBlank()) {
            return null;
        }
        JsonNode root = http.get(snapshotPath(personId));
        if (root == null || !root.isObject()) {
            return null;
        }
        // 防止网关串号、缓存错配或跨应用快照被当成本人的权限使用
        if (!matches(root, "personId", personId) || !matches(root, "appCode", appCode)) {
            return null;
        }
        JsonNode versionNode = root.get("version");
        if (versionNode == null || !versionNode.isIntegralNumber()
                || !versionNode.canConvertToLong() || versionNode.longValue() < 0) {
            return null;
        }
        JsonNode permissions = root.get("permissions");
        if (permissions == null || !permissions.isArray()) {
            return null;
        }
        Set<String> codes = new LinkedHashSet<>();
        for (JsonNode node : permissions) {
            if (!node.isTextual()) {
                return null;
            }
            String code = node.asText().trim();
            if (!code.isEmpty()) {
                codes.add(code);
            }
        }
        return new AuthorizationSnapshot(appCode, personId, versionNode.longValue(), codes);
    }

    @Override
    public Set<String> permissionsFor(String personId) {
        AuthorizationSnapshot snapshot = snapshotFor(personId);
        return snapshot == null ? null : snapshot.permissions();
    }

    private static boolean matches(JsonNode root, String field, String expected) {
        JsonNode node = root.get(field);
        return node != null && node.isTextual() && expected.equals(node.asText());
    }

    private String snapshotPath(String personId) {
        String encoded = URLEncoder.encode(personId, StandardCharsets.UTF_8).replace("+", "%20");
        return String.format(SNAPSHOT_PATH, appCode, encoded);
    }
}
