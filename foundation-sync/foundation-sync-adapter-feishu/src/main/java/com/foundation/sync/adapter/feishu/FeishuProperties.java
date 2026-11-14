package com.foundation.sync.adapter.feishu;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 飞书通讯录同步适配器配置（F-08 BAS-12）。
 *
 * <p>前缀 {@code foundation.sync.adapter.feishu}；凭证只存引用（{@code appSecretRef}），
 * 实际密钥取值走 ADR-042（{@code credential_ref}），{@code appSecret} 仅为占位与本地联调保留。
 */
@ConfigurationProperties(prefix = "foundation.sync.adapter.feishu")
public class FeishuProperties {

    /** 飞书开放平台基地址。 */
    private String baseUrl = "https://open.feishu.cn";

    /** 应用 App ID。 */
    private String appId = "";

    /** 应用密钥引用（ADR-042：只存引用不存值）。 */
    private String appSecretRef = "";

    /** 应用密钥占位（正式经 credential_ref/ADR-042 解析）。 */
    private String appSecret = "";

    /** 部门编码前缀（配合 {@code department-code-prefix} 转换函数）。 */
    private String departmentCodePrefix = "";

    /** 分页大小。 */
    private int pageSize = 200;

    /** 限流 QPS。 */
    private int qps = 10;

    /** 失败最大重试次数。 */
    private int maxRetries = 3;

    /** 重试基础退避毫秒。 */
    private long backoffMs = 500;

    /** 事件回调校验 token。 */
    private String eventVerificationToken = "";

    /** 事件回调加密密钥。 */
    private String eventEncryptKey = "";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecretRef() {
        return appSecretRef;
    }

    public void setAppSecretRef(String appSecretRef) {
        this.appSecretRef = appSecretRef;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getDepartmentCodePrefix() {
        return departmentCodePrefix;
    }

    public void setDepartmentCodePrefix(String departmentCodePrefix) {
        this.departmentCodePrefix = departmentCodePrefix;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getQps() {
        return qps;
    }

    public void setQps(int qps) {
        this.qps = qps;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getBackoffMs() {
        return backoffMs;
    }

    public void setBackoffMs(long backoffMs) {
        this.backoffMs = backoffMs;
    }

    public String getEventVerificationToken() {
        return eventVerificationToken;
    }

    public void setEventVerificationToken(String eventVerificationToken) {
        this.eventVerificationToken = eventVerificationToken;
    }

    public String getEventEncryptKey() {
        return eventEncryptKey;
    }

    public void setEventEncryptKey(String eventEncryptKey) {
        this.eventEncryptKey = eventEncryptKey;
    }
}
