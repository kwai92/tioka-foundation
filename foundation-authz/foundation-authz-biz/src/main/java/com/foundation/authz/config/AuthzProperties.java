package com.foundation.authz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 权限中心配置（F-05 BAS-05 §清单导入规则）。 */
@ConfigurationProperties(prefix = "foundation.authz")
public class AuthzProperties {

    /** 清单中消失的菜单/权限码保留天数，到期由清理任务删除。 */
    private int manifestOfflineRetentionDays = 90;

    public int getManifestOfflineRetentionDays() {
        return manifestOfflineRetentionDays;
    }

    public void setManifestOfflineRetentionDays(int manifestOfflineRetentionDays) {
        this.manifestOfflineRetentionDays = manifestOfflineRetentionDays;
    }
    public boolean isOrgAdminScopeEnforced() {
        return orgAdminScopeEnforced;
    }
    public void setOrgAdminScopeEnforced(boolean orgAdminScopeEnforced) {
        this.orgAdminScopeEnforced = orgAdminScopeEnforced;
    }

    /**
     * 分级管理员越权拦截（F-05 BAS-27 §分级授权）：开启时已配置 {@code fdn_org_admin_scope} 的
     * 操作人只能把角色授予其范围内（组织子树 + 角色白名单）的对象，越权抛 {@code FORBIDDEN} 并写审计。
     *
     * <p>默认开启（S15 §15 安全默认值，与 {@code foundation.authz.separation.*} 同口径）。
     * 未配置任何范围的操作人不受本约束——见 {@code OrgAdminScopeGuard} 的豁免规则。
     */
    private boolean orgAdminScopeEnforced = true;

    /** 平台自身应用编码（{@code /me} 未指定 {@code app} 时取该应用的快照）；默认对齐 V3 种子应用。 */
    private String platformAppCode = "foundation";

    public String getPlatformAppCode() {
        return platformAppCode;
    }

    public void setPlatformAppCode(String platformAppCode) {
        this.platformAppCode = platformAppCode;
    }
}
