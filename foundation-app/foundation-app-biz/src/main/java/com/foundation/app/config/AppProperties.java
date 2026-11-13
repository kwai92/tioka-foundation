package com.foundation.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用与权限中心配置（F-05 BAS-04 §配置，前缀 {@code foundation.app}）。
 */
@ConfigurationProperties(prefix = "foundation.app")
public class AppProperties {

    /**
     * 应用默认授权模式（F-05 BAS-04 §配置）：{@code CONCENTRATED}（集中）/ {@code DISTRIBUTED}（分散）。
     */
    private String defaultAuthMode = "CONCENTRATED";

    /**
     * 客户端密钥轮换周期（天），到期提示轮换（F-05 BAS-04 §配置）。
     */
    private int clientSecretRotationDays = 180;

    /**
     * 服务账号客户端 client_id 模板，{@code {appCode}} 为占位（F-05 BAS-04 §配置）。
     */
    private String serviceAccountClientIdPattern = "{appCode}-sa";

    public String getDefaultAuthMode() {
        return defaultAuthMode;
    }

    public void setDefaultAuthMode(String defaultAuthMode) {
        this.defaultAuthMode = defaultAuthMode;
    }

    public int getClientSecretRotationDays() {
        return clientSecretRotationDays;
    }

    public void setClientSecretRotationDays(int clientSecretRotationDays) {
        this.clientSecretRotationDays = clientSecretRotationDays;
    }

    public String getServiceAccountClientIdPattern() {
        return serviceAccountClientIdPattern;
    }

    public void setServiceAccountClientIdPattern(String serviceAccountClientIdPattern) {
        this.serviceAccountClientIdPattern = serviceAccountClientIdPattern;
    }

    /** 按应用编码生成服务账号客户端 client_id。 */
    public String serviceAccountClientId(String appCode) {
        return serviceAccountClientIdPattern.replace("{appCode}", appCode);
    }
}
