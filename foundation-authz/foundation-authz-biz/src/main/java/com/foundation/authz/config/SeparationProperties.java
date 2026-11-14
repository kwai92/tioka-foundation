package com.foundation.authz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 三员分立与高风险授权复核配置（F-09 BAS-19 §配置）。
 *
 * <p>前缀 {@code foundation.authz.separation}；默认两者均开启（S15 §15 安全默认值）。
 */
@ConfigurationProperties(prefix = "foundation.authz.separation")
public class SeparationProperties {

    /** 三员互斥约束：开启时同一人不能同时被授予系统管理员 / 安全管理员 / 审计管理员。 */
    private boolean enabled = true;

    /** 高风险授权复核：开启时含 {@code risk_level=HIGH} 权限码的授权须安全管理员复核后生效。 */
    private boolean highRiskReview = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isHighRiskReview() {
        return highRiskReview;
    }

    public void setHighRiskReview(boolean highRiskReview) {
        this.highRiskReview = highRiskReview;
    }
}
