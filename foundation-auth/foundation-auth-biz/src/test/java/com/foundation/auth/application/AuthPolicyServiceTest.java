package com.foundation.auth.application;

import com.foundation.auth.api.AuthApplyResult;
import com.foundation.auth.api.AuthPolicyCmd;
import com.foundation.auth.api.AuthPolicyView;
import com.foundation.auth.api.LoginModeCmd;
import com.foundation.auth.config.AuthProperties;
import com.foundation.auth.domain.AuthPolicy;
import com.foundation.auth.domain.LoginMode;
import com.foundation.common.api.error.BizException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-04 BAS-15 §2 §4：策略读取、修改下发与失败项汇总。 */
class AuthPolicyServiceTest {

    private final AuthTestDoubles.InMemoryAuthPolicyRepository repository =
            new AuthTestDoubles.InMemoryAuthPolicyRepository();
    private final AuthTestDoubles.FakeRealmAdmin realm = new AuthTestDoubles.FakeRealmAdmin();

    private AuthPolicyService service() {
        return new AuthPolicyService(repository, realm, new AuthProperties());
    }

    private void seedPolicy(List<String> totpRoles) {
        repository.policy = new AuthPolicy("pol_x", "length(8) and digits(1) and upperCase(1)",
                30, 10, 0, totpRoles, null, null);
    }

    private void seedModes() {
        repository.updateLoginMode(new LoginMode("alm_password", "password", "账号密码", true, 10, false, null));
        repository.updateLoginMode(new LoginMode("alm_oidc", "oidc", "企业 IdP（OIDC）", true, 20, true, "hint"));
        repository.updateLoginMode(new LoginMode("alm_ldap", "ldap", "LDAP / AD", true, 30, true, "hint"));
    }

    @Test
    void policyFallsBackToPropertiesWhenNoRow() {
        AuthPolicyView view = service().policy();

        assertThat(view.passwordPolicy()).isEqualTo("length(8) and digits(1) and upperCase(1)");
        assertThat(view.sessionIdleMinutes()).isEqualTo(30);
        assertThat(view.sessionMaxHours()).isEqualTo(10);
        assertThat(view.concurrentSessions()).isZero();
        assertThat(view.totpRequiredRoles()).containsExactly("system_admin", "security_admin");
        assertThat(repository.policy).isNull();
    }

    @Test
    void updatePolicyMergesPersistsAndApplies() {
        seedPolicy(List.of("security_admin"));
        seedModes();

        AuthApplyResult result = service().updatePolicy(new AuthPolicyCmd("length(12)", 45, null, null, null), "alice");

        assertThat(result.success()).isTrue();
        assertThat(result.items()).extracting(item -> item.item())
                .containsExactly("password", "session", "login-mode:oidc", "login-mode:ldap", "required-actions");
        assertThat(repository.policy.passwordPolicy()).isEqualTo("length(12)");
        assertThat(repository.policy.sessionIdleMinutes()).isEqualTo(45);
        assertThat(repository.policy.sessionMaxHours()).isEqualTo(10);
        assertThat(realm.calls).contains("password:length(12)", "session:45/10/0",
                "login-mode:oidc:true", "login-mode:ldap:true", "required-actions:CONFIGURE_TOTP");
        // 账号密码为本地凭证，不下发身份代理
        assertThat(realm.calls).noneMatch(call -> call.startsWith("login-mode:password"));
    }

    @Test
    void updatePolicyRejectsInvalidFieldBeforeAnyRealmCall() {
        seedPolicy(List.of("security_admin"));

        assertThatThrownBy(() -> service().updatePolicy(new AuthPolicyCmd("bad", null, null, null, null), "alice"))
                .isInstanceOf(BizException.class);
        assertThat(realm.calls).isEmpty();
    }

    @Test
    void applySkipsRequiredActionsWhenNoTotpRoles() {
        seedPolicy(List.of());

        AuthApplyResult result = service().apply("alice");

        assertThat(result.success()).isTrue();
        assertThat(result.items()).extracting(item -> item.item())
                .containsExactly("password", "session");
        assertThat(realm.calls).noneMatch(call -> call.startsWith("required-actions"));
    }

    @Test
    void applyReportsFailuresWithDetail() {
        seedPolicy(List.of("security_admin"));
        seedModes();
        realm.fail("password");
        realm.fail("login-mode:oidc");

        AuthApplyResult result = service().apply("alice");

        assertThat(result.success()).isFalse();
        assertThat(result.items()).filteredOn(item -> "password".equals(item.item()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.success()).isFalse();
                    assertThat(item.detail()).contains("Keycloak 拒绝：password");
                });
        assertThat(result.items()).filteredOn(item -> "login-mode:oidc".equals(item.item()))
                .singleElement()
                .satisfies(item -> assertThat(item.success()).isFalse());
        assertThat(result.items()).filteredOn(item -> "session".equals(item.item()))
                .singleElement()
                .satisfies(item -> assertThat(item.success()).isTrue());
    }

    @Test
    void updateLoginModesPersistsAndPushesOnlyTouched() {
        seedPolicy(List.of("security_admin"));
        seedModes();

        AuthApplyResult result = service().updateLoginModes(List.of(new LoginModeCmd("ldap", false, 5)), "alice");

        assertThat(result.success()).isTrue();
        LoginMode ldap = repository.findLoginMode("ldap").orElseThrow();
        assertThat(ldap.enabled()).isFalse();
        assertThat(ldap.sortOrder()).isEqualTo(5);
        assertThat(realm.calls).contains("login-mode:ldap:false", "password:length(8) and digits(1) and upperCase(1)");
        assertThat(realm.calls).noneMatch(call -> call.startsWith("login-mode:oidc"));
    }

    @Test
    void updateLoginModesRejectsUnknownOrEmpty() {
        seedModes();

        assertThatThrownBy(() -> service().updateLoginModes(List.of(new LoginModeCmd("kerberos", true, 1)), "alice"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service().updateLoginModes(List.of(), "alice"))
                .isInstanceOf(BizException.class);
    }
}
