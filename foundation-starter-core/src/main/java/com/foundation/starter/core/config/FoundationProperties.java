package com.foundation.starter.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 接入总配置（F-07 §13：{@code foundation.*} 根级键）。
 *
 * <p>{@code security} / {@code audit} / {@code client} 三个 starter 共用本组键：总开关、应用编码、
 * Foundation 基址与服务账号凭据。凭据只写不读，经环境变量注入（F-07 §11）。
 */
@ConfigurationProperties(prefix = "foundation")
public class FoundationProperties {

    /** 接入总开关；关时各 starter 自动配置不激活（便于本地模式）。 */
    private boolean enabled = true;

    /** 应用编码（BAS-04 登记，正则 {@code ^[a-z][a-z0-9-]{1,31}$}）。 */
    private String appCode;

    /** Foundation 服务基址。 */
    private String baseUrl = "http://localhost:8080";

    /**
     * 部署标识（ADP-07 / P5）：同一镜像在多个环境/多套部署中隔离 realm、topic、键前缀、
     * bucket 前缀与库 schema。缺省 {@code default} 时派生值与历史字面量完全一致（零改动兼容），
     * 生产环境使用缺省值会打 WARN。格式见 {@code DeploymentNamespace}。
     */
    private String deploymentId = "default";

    /** 服务账号（{@code client_credentials}）。 */
    private final ServiceAccount serviceAccount = new ServiceAccount();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public ServiceAccount getServiceAccount() {
        return serviceAccount;
    }

    /** 服务账号凭据（F-07 §11）。 */
    public static class ServiceAccount {

        private String clientId;
        private String clientSecret;

        /** OIDC 令牌端点（{@code client_credentials} 换 access token）；缺省则不换取令牌。 */
        private String tokenUri;

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

        public String getTokenUri() {
            return tokenUri;
        }

        public void setTokenUri(String tokenUri) {
            this.tokenUri = tokenUri;
        }
    }
}
