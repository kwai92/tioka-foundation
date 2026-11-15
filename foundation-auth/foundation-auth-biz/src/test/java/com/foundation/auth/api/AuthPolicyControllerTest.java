package com.foundation.auth.api;

import com.foundation.starter.security.principal.PrincipalHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** F-04 BAS-15 §4：登录方式与策略控制器委派与操作者口径。 */
class AuthPolicyControllerTest {

    private final RecordingApi api = new RecordingApi();

    @AfterEach
    void tearDown() {
        PrincipalHolder.clear();
    }

    @Test
    void delegatesReadsAndWritesToApi() {
        AuthPolicyController controller = new AuthPolicyController(api);

        assertThat(controller.policy()).isSameAs(api.view);
        assertThat(controller.loginModes()).isEqualTo(api.modes);

        AuthPolicyCmd cmd = new AuthPolicyCmd("length(8)", null, null, null, null);
        assertThat(controller.updatePolicy(cmd)).isSameAs(api.result);
        assertThat(api.lastPolicyCmd).isSameAs(cmd);

        List<LoginModeCmd> modes = List.of(new LoginModeCmd("oidc", true, 20));
        assertThat(controller.updateLoginModes(modes)).isSameAs(api.result);
        assertThat(api.lastModeCmds).isEqualTo(modes);

        assertThat(controller.apply()).isSameAs(api.result);
    }

    @Test
    void usesSystemOperatorWhenNoPrincipal() {
        AuthPolicyController controller = new AuthPolicyController(api);

        controller.apply();

        assertThat(api.operator).isEqualTo("system");
    }

    /** 记录入参与操作者的契约替身。 */
    private static final class RecordingApi implements AuthPolicyApi {

        final AuthPolicyView view = new AuthPolicyView("length(8)", 30, 10, 0, List.of("security_admin"));
        final List<LoginModeView> modes = List.of(new LoginModeView("oidc", "企业 IdP（OIDC）", true, 20, true, "hint"));
        final AuthApplyResult result = new AuthApplyResult(true, List.of(new AuthApplyItem("password", true, null)));

        AuthPolicyCmd lastPolicyCmd;
        List<LoginModeCmd> lastModeCmds;
        String operator;

        @Override
        public AuthPolicyView policy() {
            return view;
        }

        @Override
        public List<LoginModeView> loginModes() {
            return modes;
        }

        @Override
        public AuthApplyResult updatePolicy(AuthPolicyCmd cmd, String operator) {
            this.lastPolicyCmd = cmd;
            this.operator = operator;
            return result;
        }

        @Override
        public AuthApplyResult updateLoginModes(List<LoginModeCmd> commands, String operator) {
            this.lastModeCmds = commands;
            this.operator = operator;
            return result;
        }

        @Override
        public AuthApplyResult apply(String operator) {
            this.operator = operator;
            return result;
        }
    }
}
