package com.foundation.auth.api;

import java.util.List;

/**
 * 登录方式与策略管理契约（F-04 BAS-15 §4）。
 *
 * <p>登录协议全部由 Keycloak 实现，本契约只做「读取策略 / 修改并下发 realm / 重下发」，
 * 下发逐项结果由 {@link AuthApplyResult} 返回（失败不静默）。
 */
public interface AuthPolicyApi {

    /** 读取当前密码 / 会话策略与 TOTP 强制角色。 */
    AuthPolicyView policy();

    /** 登录方式与启用状态（按排序号升序）。 */
    List<LoginModeView> loginModes();

    /** 修改策略并下发 realm（含 TOTP 必需动作）。 */
    AuthApplyResult updatePolicy(AuthPolicyCmd cmd, String operator);

    /** 启用 / 排序登录方式并下发 realm（简单项下发；复杂项返回控制台引导）。 */
    AuthApplyResult updateLoginModes(List<LoginModeCmd> commands, String operator);

    /** 按当前持久化配置重新下发并返回逐项结果。 */
    AuthApplyResult apply(String operator);
}
