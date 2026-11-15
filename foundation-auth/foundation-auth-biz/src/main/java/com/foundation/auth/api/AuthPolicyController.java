package com.foundation.auth.api;

import com.foundation.starter.audit.annotation.Audited;
import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 登录方式与策略 REST（F-04 BAS-15 §4）。
 *
 * <p>读接口需登录（{@code fdn:auth:policy:read}）；写接口需 {@code fdn:auth:policy:admin}
 * （安全管理员域），修改写审计。本类不标 {@code @RestController}：由 {@code AuthAutoConfiguration} 以 {@code @Bean} 装配。
 */
@ResponseBody
@RequestMapping("/api/auth")
public class AuthPolicyController {

    private static final String READ = "fdn:auth:policy:read";
    private static final String ADMIN = "fdn:auth:policy:admin";

    private final AuthPolicyApi api;

    public AuthPolicyController(AuthPolicyApi api) {
        this.api = api;
    }

    /** 读取当前密码 / 会话策略。 */
    @GetMapping("/policy")
    @RequirePermission(READ)
    public AuthPolicyView policy() {
        return api.policy();
    }

    /** 修改策略并下发 realm。 */
    @PutMapping("/policy")
    @RequirePermission(ADMIN)
    @Audited(action = "fdn.auth.policy.update", resource = "fdn_auth_policy")
    public AuthApplyResult updatePolicy(@RequestBody AuthPolicyCmd cmd) {
        return api.updatePolicy(cmd, operator());
    }

    /** 登录方式与启用状态。 */
    @GetMapping("/login-modes")
    @RequirePermission(READ)
    public List<LoginModeView> loginModes() {
        return api.loginModes();
    }

    /** 启用 / 排序 / 强制 TOTP（简单项下发；复杂项返回控制台引导）。 */
    @PutMapping("/login-modes")
    @RequirePermission(ADMIN)
    @Audited(action = "fdn.auth.login-modes.update", resource = "fdn_auth_login_mode")
    public AuthApplyResult updateLoginModes(@RequestBody List<LoginModeCmd> commands) {
        return api.updateLoginModes(commands, operator());
    }

    /** 重新下发并返回逐项结果。 */
    @PostMapping("/policy/apply")
    @RequirePermission(ADMIN)
    @Audited(action = "fdn.auth.policy.apply", resource = "fdn_auth_policy")
    public AuthApplyResult apply() {
        return api.apply(operator());
    }

    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }
}
