package com.foundation.authz.domain;

import java.util.Objects;

/**
 * 子系统（F-05 BAS-27 §子系统）：与应用（BAS-04 {@code fdn_app}）以 {@code app_id} 1:1 关联。
 *
 * @param id            子系统 ID（{@code sub_{ulid}}）
 * @param appId         所属应用 ID
 * @param authMode      授权模式，见 {@link AuthMode}
 * @param portalVisible 是否在门户可见
 * @param adminUserId   子系统管理员用户 ID，可空
 */
public record Subsystem(String id,
                        String appId,
                        AuthMode authMode,
                        boolean portalVisible,
                        String adminUserId) {

    public Subsystem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(appId, "appId");
        authMode = authMode == null ? AuthMode.CONCENTRATED : authMode;
    }

    /** 副本：变更授权模式。 */
    public Subsystem withAuthMode(AuthMode newAuthMode) {
        return new Subsystem(id, appId, newAuthMode, portalVisible, adminUserId);
    }

    /** 副本：变更门户可见性。 */
    public Subsystem withPortalVisible(boolean newPortalVisible) {
        return new Subsystem(id, appId, authMode, newPortalVisible, adminUserId);
    }

    /** 副本：变更子系统管理员。 */
    public Subsystem withAdminUserId(String newAdminUserId) {
        return new Subsystem(id, appId, authMode, portalVisible, newAdminUserId);
    }
}
