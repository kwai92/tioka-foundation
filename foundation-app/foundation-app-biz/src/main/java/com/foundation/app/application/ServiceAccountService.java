package com.foundation.app.application;

import com.foundation.app.api.ServiceAccountApi;
import com.foundation.app.api.ServiceAccountCmd;
import com.foundation.app.api.ServiceAccountView;
import com.foundation.app.config.AppProperties;
import com.foundation.app.domain.App;
import com.foundation.app.domain.AppIdPrefix;
import com.foundation.app.domain.ServiceAccount;
import com.foundation.app.infrastructure.AppRepository;
import com.foundation.app.infrastructure.ServiceAccountRepository;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.api.keycloak.KeycloakOidcClientAdmin;
import com.foundation.common.api.keycloak.OidcClientSpec;
import com.foundation.common.id.Ids;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 服务账号管理（F-05 BAS-04 §接口）。
 *
 * <p>账号主体（{@code fdn_account}，{@code type=SERVICE}）由 BAS-03 建立，本项只存绑定行与权限码白名单；
 * 客户端按 {@code foundation.app.service-account-client-id-pattern} 生成并只开 {@code client_credentials}。
 */
public class ServiceAccountService implements ServiceAccountApi {

    private final AppRepository apps;
    private final ServiceAccountRepository accounts;
    private final KeycloakOidcClientAdmin keycloak;
    private final AppProperties properties;

    public ServiceAccountService(AppRepository apps, ServiceAccountRepository accounts,
                                 KeycloakOidcClientAdmin keycloak, AppProperties properties) {
        this.apps = apps;
        this.accounts = accounts;
        this.keycloak = keycloak;
        this.properties = properties;
    }

    @Override
    public List<ServiceAccountView> listByApp(String appCode) {
        App app = requireApp(appCode);
        return accounts.listByAppId(app.id()).stream().map(ServiceAccountService::toView).toList();
    }

    @Override
    @Transactional
    public String create(ServiceAccountCmd cmd) {
        App app = requireApp(cmd.appCode());
        String name = cmd.name() == null ? "" : cmd.name().trim();
        if (accounts.findByName(app.id(), name).isPresent()) {
            throw new BizException(ErrorCode.CONFLICT, "应用 " + app.code() + " 下服务账号名称已存在：" + name);
        }
        String operator = operator(cmd.operator());
        String clientId = properties.serviceAccountClientId(app.code());
        ServiceAccount account = ServiceAccount.of(Ids.newId(AppIdPrefix.SERVICE_ACCOUNT), cmd.accountId(),
                app.id(), clientId, name, cmd.permissionCodes(), cmd.ownerUserId(), cmd.expiresAt());
        accounts.insert(account, operator);

        // 服务账号客户端只开 client_credentials，无回调地址（S15 §8.2）
        keycloak.upsert(new OidcClientSpec(clientId, app.name() + " 服务账号", List.of(), List.of("client_credentials")));
        return account.id();
    }

    @Override
    @Transactional
    public void disable(String serviceAccountId, String operator) {
        ServiceAccount account = accounts.findById(serviceAccountId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "服务账号不存在：" + serviceAccountId));
        accounts.updateStatus(account.id(), ServiceAccount.STATUS_DISABLED, operator(operator));
    }

    @Override
    @Transactional
    public int disableExpired(Instant now) {
        return accounts.disableExpired(now);
    }

    @Override
    @Transactional
    public void transferOwner(String serviceAccountId, String newOwnerPersonId, String operator) {
        if (newOwnerPersonId == null || newOwnerPersonId.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "新负责人必填");
        }
        ServiceAccount account = accounts.findById(serviceAccountId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "服务账号不存在：" + serviceAccountId));
        accounts.updateOwner(account.id(), newOwnerPersonId, operator(operator));
    }

    private App requireApp(String appCode) {
        return apps.find(appCode)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "应用不存在：" + appCode));
    }

    private static ServiceAccountView toView(ServiceAccount account) {
        return new ServiceAccountView(account.id(), account.accountId(), account.appId(), account.clientId(),
                account.name(), account.permissionCodes(), account.ownerUserId(), account.status(),
                account.expiresAt());
    }

    private static String operator(String operator) {
        return operator == null || operator.isBlank() ? "system" : operator;
    }
}
