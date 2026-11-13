package com.foundation.app.domain;

import java.time.Instant;
import java.util.List;

/**
 * 应用客户端（F-05 BAS-04 §领域模型）：一个应用可有一个或多个 OIDC 客户端，登记后在 Keycloak 自动开通。
 *
 * <p>密钥明文不落库，由 Keycloak 保管；本地只记 {@code secretRotatedAt}。
 */
public record AppClient(String id,
                        String appId,
                        String clientId,
                        List<String> redirectUris,
                        List<String> grantTypes,
                        String status,
                        Instant secretRotatedAt) {

    /** 全部开通成功。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 登记/轮换进行中（本地已落库，Keycloak 尚未确认）。 */
    public static final String STATUS_ROTATING = "ROTATING";

    public static final String STATUS_DISABLED = "DISABLED";

    /** Keycloak 开通成功：置 {@code ACTIVE}。 */
    public AppClient activated() {
        return new AppClient(id, appId, clientId, redirectUris, grantTypes, STATUS_ACTIVE, secretRotatedAt);
    }

    /** 密钥轮换完成：只更新轮换时间，密钥明文不落库。 */
    public AppClient secretRotatedAt(Instant rotatedAt) {
        return new AppClient(id, appId, clientId, redirectUris, grantTypes, status, rotatedAt);
    }

    public AppClient disabled() {
        return new AppClient(id, appId, clientId, redirectUris, grantTypes, STATUS_DISABLED, secretRotatedAt);
    }
}
