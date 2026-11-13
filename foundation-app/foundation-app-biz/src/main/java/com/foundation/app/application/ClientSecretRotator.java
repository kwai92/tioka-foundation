package com.foundation.app.application;

import com.foundation.app.api.AppClientApi;
import com.foundation.app.api.ClientView;
import com.foundation.app.domain.App;
import com.foundation.app.domain.AppClient;
import com.foundation.app.infrastructure.AppClientRepository;
import com.foundation.app.infrastructure.AppRepository;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.api.keycloak.KeycloakOidcClientAdmin;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 客户端密钥轮换（F-05 BAS-04 §自动开通与密钥轮换）。
 *
 * <p>轮换由 Keycloak 生成新密钥并立即作废旧密钥（新旧并存窗口默认 0 秒），本地只更新
 * {@code secret_rotated_at}；密钥明文不落库，仅经返回值交付。
 */
public class ClientSecretRotator implements AppClientApi {

    private final AppRepository apps;
    private final AppClientRepository clients;
    private final KeycloakOidcClientAdmin keycloak;

    public ClientSecretRotator(AppRepository apps, AppClientRepository clients, KeycloakOidcClientAdmin keycloak) {
        this.apps = apps;
        this.clients = clients;
        this.keycloak = keycloak;
    }

    @Override
    public List<ClientView> listByApp(String appCode) {
        App app = requireApp(appCode);
        return clients.listByAppId(app.id()).stream().map(ClientSecretRotator::toView).toList();
    }

    @Override
    @Transactional
    public String rotateSecret(String appCode, String clientId, String operator) {
        App app = requireApp(appCode);
        AppClient client = clients.find(app.id(), clientId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND,
                        "应用 " + appCode + " 下不存在客户端：" + clientId));
        String secret = keycloak.regenerateSecret(clientId);
        clients.markSecretRotated(client.id(), Instant.now(), operator(operator));
        return secret;
    }

    private App requireApp(String appCode) {
        return apps.find(appCode)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "应用不存在：" + appCode));
    }

    private static ClientView toView(AppClient client) {
        return new ClientView(client.id(), client.appId(), client.clientId(), client.redirectUris(),
                client.grantTypes(), client.status(), client.secretRotatedAt());
    }

    private static String operator(String operator) {
        return operator == null || operator.isBlank() ? "system" : operator;
    }
}
