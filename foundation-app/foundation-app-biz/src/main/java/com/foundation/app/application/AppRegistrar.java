package com.foundation.app.application;

import com.foundation.app.api.AppApi;
import com.foundation.app.api.AppRegistrationCmd;
import com.foundation.app.api.AppUpdateCmd;
import com.foundation.app.api.AppView;
import com.foundation.app.domain.App;
import com.foundation.app.domain.AppClient;
import com.foundation.app.domain.AppCode;
import com.foundation.app.domain.AppIdPrefix;
import com.foundation.app.domain.AppKind;
import com.foundation.app.domain.ClientRegistration;
import com.foundation.app.infrastructure.AppClientRepository;
import com.foundation.app.infrastructure.AppRepository;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.api.keycloak.KeycloakOidcClientAdmin;
import com.foundation.common.api.keycloak.OidcClientSpec;
import com.foundation.common.id.Ids;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 应用登记（F-05 BAS-04 §自动开通与密钥轮换）。
 *
 * <p>Keycloak 调用在 {@link #register} 的同一事务内、数据库写入之后：Keycloak 报错时异常外抛，
 * 事务回滚，本地不落半成品（§测试用例 {@code registerRollbackOnKeycloakFailure}）。
 */
public class AppRegistrar implements AppApi {

    private final AppRepository apps;
    private final AppClientRepository clients;
    private final KeycloakOidcClientAdmin keycloak;

    public AppRegistrar(AppRepository apps, AppClientRepository clients, KeycloakOidcClientAdmin keycloak) {
        this.apps = apps;
        this.clients = clients;
        this.keycloak = keycloak;
    }

    @Override
    public List<AppView> list() {
        return apps.list().stream().map(AppRegistrar::toView).toList();
    }

    @Override
    public Optional<AppView> find(String code) {
        return apps.find(code).map(AppRegistrar::toView);
    }

    @Override
    @Transactional
    public String register(AppRegistrationCmd cmd) {
        String code = AppCode.of(cmd.code()).value();
        String kind = AppKind.of(cmd.kind());
        ClientRegistration registration = ClientRegistration.of(null, cmd.redirectUris());
        if (apps.find(code).isPresent()) {
            throw new BizException(ErrorCode.CONFLICT, "应用编码已存在：" + code);
        }
        String operator = operator(cmd.operator());
        App app = App.register(Ids.newId(AppIdPrefix.APP), code, cmd.name(), cmd.icon(), cmd.accessUrl(),
                kind, cmd.ownerUserId());
        apps.insert(app, operator);

        // 登记规则：新应用的客户端 clientId 取应用编码（门户/管理台为固定值，由初始化落库）
        AppClient client = new AppClient(Ids.newId(AppIdPrefix.CLIENT), app.id(), code,
                registration.redirectUris(), registration.grantTypes(), AppClient.STATUS_ROTATING, null);
        clients.insert(client, operator);

        keycloak.upsert(new OidcClientSpec(code, app.name(), registration.redirectUris(),
                registration.grantTypes()));
        clients.updateStatus(client.id(), AppClient.STATUS_ACTIVE, operator);
        return app.id();
    }

    @Override
    @Transactional
    public void update(AppUpdateCmd cmd) {
        App existing = apps.find(cmd.code())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "应用不存在：" + cmd.code()));
        App updated = existing.updateProfile(cmd.name(), cmd.icon(), cmd.accessUrl(), cmd.ownerUserId(),
                cmd.status());
        apps.update(updated, operator(cmd.operator()));
    }

    private static AppView toView(App app) {
        return new AppView(app.id(), app.code(), app.name(), app.icon(), app.accessUrl(), app.kind(),
                app.ownerUserId(), app.status(), app.manifestVersion());
    }

    private static String operator(String operator) {
        return operator == null || operator.isBlank() ? "system" : operator;
    }
}
