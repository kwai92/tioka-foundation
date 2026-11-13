package com.foundation.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Keycloak 管理接口连接配置（F-02 BAS-01 §配置项，前缀 {@code foundation.keycloak}）。
 *
 * <p>Foundation 以服务账号经 {@code client_credentials} 调 Keycloak Admin REST API（S15 §4）。
 * {@code clientSecret} 只写不读，经环境变量注入，不入库不入配置中心明文。
 */
@ConfigurationProperties(prefix = "foundation.keycloak")
public class KeycloakProperties {

    /** Keycloak 基址，例如 {@code http://keycloak:8080}。 */
    private String serverUrl = "http://localhost:8180";

    /** 应用共用 realm（ADR-051 第 57 条：单 realm）。 */
    private String realm = "enterprise";

    /** 管理接口取令牌所在 realm（生产为 {@code master}）。 */
    private String adminRealm = "master";

    /** Foundation 服务账号 client_id。 */
    private String clientId;

    /** Foundation 服务账号 client_secret（环境变量注入）。 */
    private String clientSecret;

    /** 连接超时。 */
    private Duration connectTimeout = Duration.ofSeconds(3);

    /** 读超时。 */
    private Duration readTimeout = Duration.ofSeconds(5);

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public String getAdminRealm() {
        return adminRealm;
    }

    public void setAdminRealm(String adminRealm) {
        this.adminRealm = adminRealm;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
