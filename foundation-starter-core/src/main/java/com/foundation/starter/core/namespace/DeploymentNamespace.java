package com.foundation.starter.core.namespace;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 部署命名空间（36 号规划 ADP-07 / P5）：把同一套镜像在多个环境/多套部署中的共享资源名隔开。
 *
 * <p>约定见 36 号规划 §6「命名空间」：所有共享资源名 =
 * {@code {prefix}:{deploymentId}:{appCode}:{业务键}}。本类只负责<b>服务级</b>前缀的派生，
 * 应用级隔离仍由 {@code foundation.app-code} 完成。
 *
 * <p><b>兼容基线</b>：{@code deploymentId=default}（缺省）时所有派生值<b>逐字等于改造前的
 * 字面量</b>（{@code enterprise} / {@code fdn-events} / {@code fdn:} / {@code fdn-} /
 * {@code foundation}），因此存量部署与 751 个既有单测零改动；只有显式设置了非 default 的
 * deployment-id 才追加命名空间后缀。
 *
 * <p><b>刻意不做的事</b>：不引入 {@code tenant_id}、不做按租户的数据分区（ADR-029 第 60 条）。
 * deployment-id 解决的是同一客户多套部署/多环境的资源命名冲突，不是多租户隔离。
 * 服务端关系库、消息总线、认证协议实现方仍锁定 PG / RocketMQ / Keycloak（P3 边界）。
 */
public final class DeploymentNamespace {

    /** 缺省部署标识；生产环境使用缺省值会在启动日志告警。 */
    public static final String DEFAULT_ID = "default";

    /** 部署标识配置键。 */
    public static final String PROPERTY = "foundation.deployment-id";

    /** 派生默认值所在的属性源键前缀，供 application.yml 以 {@code ${foundation.namespace.*}} 引用。 */
    public static final String NAMESPACE_PREFIX = "foundation.namespace.";

    /** 改造前的共享 realm 字面量。 */
    public static final String DEFAULT_REALM = "enterprise";

    /** 改造前的事件主题字面量。 */
    public static final String DEFAULT_EVENT_TOPIC = "fdn-events";

    /** 改造前的 Redis / 缓存键前缀字面量。 */
    public static final String DEFAULT_KEY_PREFIX = "fdn:";

    /** 改造前的对象存储桶前缀字面量。 */
    public static final String DEFAULT_BUCKET_PREFIX = "fdn-";

    /** 桶名主体（与 {@code foundation.file.storage.bucket} 的既有取值一致）。 */
    public static final String DEFAULT_BUCKET_BODY = "app_foundation";

    /** 改造前的库 schema / 库名字面量。 */
    public static final String DEFAULT_DB_SCHEMA = "foundation";

    /** 缺省 Keycloak 基址。 */
    public static final String DEFAULT_KEYCLOAK_URL = "http://localhost:8180";

    /** deployment-id 只允许小写字母、数字与连字符，长度 1〜31，且不以连字符开头/结尾。 */
    private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,29}[a-z0-9])?$");

    private final String deploymentId;

    private DeploymentNamespace(String deploymentId) {
        // 严格校验、不做大小写归一化：大小写不同会被当成两套部署，容易串环境；
        // 与 deploy/init/render-deployment.sh 的正则口径保持一致。
        String normalized = deploymentId == null || deploymentId.isBlank()
                ? DEFAULT_ID : deploymentId.trim();
        if (!VALID_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "非法的 " + PROPERTY + "='" + deploymentId + "'，需匹配 ^[a-z0-9](?:[a-z0-9-]{0,29}[a-z0-9])?$"
                            + "（如 prod-a、uat2）");
        }
        this.deploymentId = normalized;
    }

    /** 由配置值构造；null / 空白等价于 default。 */
    public static DeploymentNamespace of(String deploymentId) {
        return new DeploymentNamespace(deploymentId);
    }

    /** 缺省命名空间（兼容基线，不追加任何后缀）。 */
    public static DeploymentNamespace defaults() {
        return new DeploymentNamespace(DEFAULT_ID);
    }

    /** 是否使用缺省部署标识。 */
    public boolean isDefault() {
        return DEFAULT_ID.equals(deploymentId);
    }

    public String id() {
        return deploymentId;
    }

    /** 缓存 / 锁 / 限流键前缀：{@code fdn:} 或 {@code fdn:{id}:}。 */
    public String keyPrefix() {
        return isDefault() ? DEFAULT_KEY_PREFIX : DEFAULT_KEY_PREFIX + deploymentId + ":";
    }

    /** 对象存储桶前缀：{@code fdn-} 或 {@code fdn-{id}-}。 */
    public String bucketPrefix() {
        return isDefault() ? DEFAULT_BUCKET_PREFIX : DEFAULT_BUCKET_PREFIX + deploymentId + "-";
    }

    /**
     * 完整桶名：{@code fdn-app_foundation} 或 {@code fdn-{id}-app_foundation}。
     *
     * <p>部署文档曾声明 {@code foundation.storage.bucket-prefix}，但代码只绑定
     * {@code foundation.file.storage.bucket}（见 FileProperties）；这里给出可直接绑定的完整值。
     */
    public String bucket() {
        return bucketPrefix() + DEFAULT_BUCKET_BODY;
    }

    /** Keycloak realm：{@code enterprise} 或 {@code enterprise-{id}}。 */
    public String realm() {
        return scoped(DEFAULT_REALM);
    }

    /** 事件主题：{@code fdn-events} 或 {@code fdn-events-{id}}。 */
    public String eventTopic() {
        return scoped(DEFAULT_EVENT_TOPIC);
    }

    /** 库 / schema 名：{@code foundation} 或 {@code foundation-{id}}。 */
    public String dbSchema() {
        return scoped(DEFAULT_DB_SCHEMA);
    }

    /** 通用派生：default 时原样返回基线值，否则追加 {@code -{id}}。 */
    public String scoped(String base) {
        if (base == null || base.isBlank()) {
            throw new IllegalArgumentException("命名空间基线值不能为空");
        }
        return isDefault() ? base : base + "-" + deploymentId;
    }

    /** 派生后的 JWKS 端点；realm 与 {@link #realm()} 保持一致，避免令牌 iss 校验失败。 */
    public String jwkSetUri(String keycloakUrl) {
        return trimTrailingSlash(keycloakUrl) + "/realms/" + realm()
                + "/protocol/openid-connect/certs";
    }

    /** 派生后的 issuer。 */
    public String issuer(String keycloakUrl) {
        return trimTrailingSlash(keycloakUrl) + "/realms/" + realm();
    }

    /**
     * 供 {@code EnvironmentPostProcessor} 注入的最低优先级默认属性。
     *
     * <p>application.yml 均以 {@code ${FOUNDATION_XXX:${foundation.namespace.xxx}}} 形式引用，
     * 因此显式环境变量/配置永远优先，缺省时才落到这里的派生值。
     */
    public Map<String, Object> defaults(String keycloakUrl) {
        String url = keycloakUrl == null || keycloakUrl.isBlank() ? DEFAULT_KEYCLOAK_URL : keycloakUrl;
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(NAMESPACE_PREFIX + "deployment-id", id());
        values.put(NAMESPACE_PREFIX + "realm", realm());
        values.put(NAMESPACE_PREFIX + "event-topic", eventTopic());
        values.put(NAMESPACE_PREFIX + "key-prefix", keyPrefix());
        values.put(NAMESPACE_PREFIX + "bucket-prefix", bucketPrefix());
        values.put(NAMESPACE_PREFIX + "bucket", bucket());
        values.put(NAMESPACE_PREFIX + "db-schema", dbSchema());
        values.put(NAMESPACE_PREFIX + "jwk-set-uri", jwkSetUri(url));
        values.put(NAMESPACE_PREFIX + "issuer", issuer(url));
        return values;
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
