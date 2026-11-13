package com.foundation.common.api.keycloak;

/**
 * Keycloak OIDC 客户端管理契约（F-05 BAS-04）。
 *
 * <p>跨模块只依赖 {@code -api}（F-05 硬约束）：领域 {@code -biz} 只依赖本接口，
 * 实现落在 {@code foundation-common-biz} 的 Keycloak 管理接口客户端内。
 *
 * <p>客户端密钥明文只在 {@link #regenerateSecret(String)} 的返回值中出现，
 * 用于交付给应用方；Foundation 不落库（F-05 BAS-04 §自动开通与密钥轮换）。
 */
public interface KeycloakOidcClientAdmin {

    /** 幂等登记：按 {@code clientId} 建或更新客户端（机密客户端，标准流程按 {@code grantTypes} 开启）。 */
    void upsert(OidcClientSpec spec);

    /** 重新生成客户端密钥并返回新密钥明文（Keycloak 侧旧密钥立即失效）。 */
    String regenerateSecret(String clientId);

    /** 删除客户端；不存在时静默返回。 */
    void delete(String clientId);
}
