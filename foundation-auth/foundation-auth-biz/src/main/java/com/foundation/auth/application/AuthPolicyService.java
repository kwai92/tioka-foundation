package com.foundation.auth.application;

import com.foundation.auth.api.AuthApplyItem;
import com.foundation.auth.api.AuthApplyResult;
import com.foundation.auth.api.AuthPolicyApi;
import com.foundation.auth.api.AuthPolicyCmd;
import com.foundation.auth.api.AuthPolicyView;
import com.foundation.auth.api.LoginModeCmd;
import com.foundation.auth.api.LoginModeView;
import com.foundation.auth.config.AuthProperties;
import com.foundation.auth.domain.AuthIdPrefix;
import com.foundation.auth.domain.AuthPolicy;
import com.foundation.auth.domain.AuthPolicyRules;
import com.foundation.auth.domain.LoginMode;
import com.foundation.auth.domain.LoginModes;
import com.foundation.auth.infrastructure.AuthPolicyRepository;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.api.keycloak.KeycloakRealmAdmin;
import com.foundation.common.api.keycloak.KeycloakRequiredActions;
import com.foundation.common.id.Ids;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 登录方式与策略应用服务（F-04 BAS-15 §2 §4）。
 *
 * <p>策略落库（{@code fdn_auth_policy}/{@code fdn_auth_login_mode}）后下发到 Keycloak realm；
 * 下发逐项收集结果，失败项带 Keycloak 返回，**不静默成功**（§4）。
 */
public class AuthPolicyService implements AuthPolicyApi {

    private final AuthPolicyRepository repository;
    private final KeycloakRealmAdmin realmAdmin;
    private final AuthProperties properties;

    public AuthPolicyService(AuthPolicyRepository repository, KeycloakRealmAdmin realmAdmin,
                             AuthProperties properties) {
        this.repository = repository;
        this.realmAdmin = realmAdmin;
        this.properties = properties;
    }

    @Override
    public AuthPolicyView policy() {
        return toView(currentPolicy());
    }

    @Override
    public List<LoginModeView> loginModes() {
        return repository.listLoginModes().stream().map(AuthPolicyService::toView).toList();
    }

    @Override
    @Transactional
    public AuthApplyResult updatePolicy(AuthPolicyCmd cmd, String operator) {
        AuthPolicy current = currentPolicy();
        AuthPolicy updated = new AuthPolicy(
                current.id(),
                cmd.passwordPolicy() == null
                        ? current.passwordPolicy() : AuthPolicyRules.requirePasswordPolicy(cmd.passwordPolicy()),
                cmd.sessionIdleMinutes() == null
                        ? current.sessionIdleMinutes() : AuthPolicyRules.requireSessionIdleMinutes(cmd.sessionIdleMinutes()),
                cmd.sessionMaxHours() == null
                        ? current.sessionMaxHours() : AuthPolicyRules.requireSessionMaxHours(cmd.sessionMaxHours()),
                cmd.concurrentSessions() == null
                        ? current.concurrentSessions() : AuthPolicyRules.requireConcurrentSessions(cmd.concurrentSessions()),
                cmd.totpRequiredRoles() == null
                        ? current.totpRequiredRoles() : AuthPolicyRules.normalizeTotpRoles(cmd.totpRequiredRoles()),
                current.createAt(), current.updateAt());
        repository.save(updated);
        return applyAll(updated, repository.listLoginModes(), null);
    }

    @Override
    @Transactional
    public AuthApplyResult updateLoginModes(List<LoginModeCmd> commands, String operator) {
        if (commands == null || commands.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "登录方式列表不能为空");
        }
        List<String> touched = new ArrayList<>();
        for (LoginModeCmd cmd : commands) {
            String code = cmd.modeCode();
            if (code == null || code.isBlank()) {
                throw new BizException(ErrorCode.BAD_REQUEST, "登录方式编码不能为空");
            }
            LoginMode existing = repository.findLoginMode(code)
                    .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "未知登录方式：" + code));
            LoginMode updated = new LoginMode(existing.id(), existing.modeCode(), existing.name(),
                    cmd.enabled() == null ? existing.enabled() : cmd.enabled(),
                    cmd.sortOrder() == null ? existing.sortOrder() : AuthPolicyRules.requireSortOrder(cmd.sortOrder()),
                    existing.complex(), existing.consoleHint());
            repository.updateLoginMode(updated);
            touched.add(code);
        }
        return applyAll(currentPolicy(), repository.listLoginModes(), touched);
    }

    @Override
    public AuthApplyResult apply(String operator) {
        return applyAll(currentPolicy(), repository.listLoginModes(), null);
    }

    /** 当前策略：无行时以配置默认值播种（不落库，首次保存时写入）。 */
    private AuthPolicy currentPolicy() {
        return repository.find().orElseGet(this::defaultPolicy);
    }

    private AuthPolicy defaultPolicy() {
        AuthProperties.Policy policy = properties.getPolicy();
        return new AuthPolicy(Ids.newId(AuthIdPrefix.POLICY), policy.getPassword(),
                policy.getSessionIdleMinutes(), policy.getSessionMaxHours(), policy.getConcurrentSessions(),
                AuthPolicyRules.normalizeTotpRoles(properties.getTotpRequiredRoles()), null, null);
    }

    /**
     * 下发全部策略项并汇总结果。
     *
     * @param restrictTo 非空时只下发列表内的登录方式（局部修改场景），{@code null} 表示全部
     */
    private AuthApplyResult applyAll(AuthPolicy policy, List<LoginMode> modes, List<String> restrictTo) {
        List<AuthApplyItem> items = new ArrayList<>();
        items.add(attempt("password", () -> realmAdmin.applyPasswordPolicy(policy.passwordPolicy())));
        items.add(attempt("session", () -> realmAdmin.applySessionPolicy(
                policy.sessionIdleMinutes(), policy.sessionMaxHours(), policy.concurrentSessions())));
        for (LoginMode mode : modes) {
            if (!LoginModes.requiresIdentityProvider(mode.modeCode())) {
                continue;
            }
            if (restrictTo != null && !restrictTo.contains(mode.modeCode())) {
                continue;
            }
            items.add(attempt("login-mode:" + mode.modeCode(),
                    () -> realmAdmin.applyLoginMode(mode.modeCode(), mode.enabled())));
        }
        if (!policy.totpRequiredRoles().isEmpty()) {
            items.add(attempt("required-actions",
                    () -> realmAdmin.applyRequiredActions(List.of(KeycloakRequiredActions.CONFIGURE_TOTP))));
        }
        return AuthApplyResult.of(items);
    }

    private static AuthApplyItem attempt(String item, Runnable action) {
        try {
            action.run();
            return new AuthApplyItem(item, true, null);
        } catch (RuntimeException e) {
            return new AuthApplyItem(item, false, e.getMessage());
        }
    }

    private static AuthPolicyView toView(AuthPolicy policy) {
        return new AuthPolicyView(policy.passwordPolicy(), policy.sessionIdleMinutes(),
                policy.sessionMaxHours(), policy.concurrentSessions(), policy.totpRequiredRoles());
    }

    private static LoginModeView toView(LoginMode mode) {
        return new LoginModeView(mode.modeCode(), mode.name(), mode.enabled(), mode.sortOrder(),
                mode.complex(), mode.consoleHint());
    }
}
