package com.foundation.auth.application;

import com.foundation.auth.domain.AuthPolicy;
import com.foundation.auth.domain.LoginMode;
import com.foundation.auth.infrastructure.AuthPolicyRepository;
import com.foundation.common.api.keycloak.KeycloakRealmAdmin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 登录方式应用层单测的内存替身。 */
final class AuthTestDoubles {

    private AuthTestDoubles() {
    }

    /** 内存登录策略仓储。 */
    static final class InMemoryAuthPolicyRepository implements AuthPolicyRepository {

        AuthPolicy policy;
        final Map<String, LoginMode> modes = new LinkedHashMap<>();

        @Override
        public Optional<AuthPolicy> find() {
            return Optional.ofNullable(policy);
        }

        @Override
        public void save(AuthPolicy value) {
            this.policy = value;
        }

        @Override
        public List<LoginMode> listLoginModes() {
            return modes.values().stream()
                    .sorted(Comparator.comparingInt(LoginMode::sortOrder).thenComparing(LoginMode::modeCode))
                    .toList();
        }

        @Override
        public Optional<LoginMode> findLoginMode(String modeCode) {
            return Optional.ofNullable(modes.get(modeCode));
        }

        @Override
        public void updateLoginMode(LoginMode mode) {
            modes.put(mode.modeCode(), mode);
        }
    }

    /** 记录调用、可按项注入失败的下发替身。 */
    static final class FakeRealmAdmin implements KeycloakRealmAdmin {

        final List<String> calls = new ArrayList<>();
        final Set<String> failOn = new HashSet<>();

        void fail(String item) {
            failOn.add(item);
        }

        private void maybeFail(String item) {
            if (failOn.contains(item)) {
                throw new IllegalStateException("Keycloak 拒绝：" + item);
            }
        }

        @Override
        public void applyPasswordPolicy(String passwordPolicy) {
            maybeFail("password");
            calls.add("password:" + passwordPolicy);
        }

        @Override
        public void applySessionPolicy(int idleMinutes, int maxHours, int concurrentSessions) {
            maybeFail("session");
            calls.add("session:" + idleMinutes + "/" + maxHours + "/" + concurrentSessions);
        }

        @Override
        public void applyLoginMode(String modeCode, boolean enabled) {
            maybeFail("login-mode:" + modeCode);
            calls.add("login-mode:" + modeCode + ":" + enabled);
        }

        @Override
        public void applyRequiredActions(List<String> requiredActions) {
            maybeFail("required-actions");
            calls.add("required-actions:" + String.join(",", requiredActions));
        }
    }
}
