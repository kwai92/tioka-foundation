package com.foundation.app.api;

import java.util.List;

/**
 * 应用客户端契约（F-05 BAS-04 §接口 {@code POST /api/apps/{code}/clients/{clientId}/rotate-secret}）。
 */
public interface AppClientApi {

    /** 某应用下的客户端列表（按 clientId 升序）。 */
    List<ClientView> listByApp(String appCode);

    /**
     * 轮换客户端密钥：Keycloak 生成新密钥并立即作废旧密钥，本地只更新 {@code secret_rotated_at}。
     *
     * @param operator 操作人（审计列）
     * @return 新密钥明文（唯一交付出口，Foundation 不落库）
     */
    String rotateSecret(String appCode, String clientId, String operator);
}
