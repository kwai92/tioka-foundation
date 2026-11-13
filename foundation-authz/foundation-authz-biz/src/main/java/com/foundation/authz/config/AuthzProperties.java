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
}
