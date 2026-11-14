package com.foundation.authz.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/** 授权模式切换规则（F-05 BAS-27 §模式切换）。 */
public final class AuthModeRules {

    private AuthModeRules() {
    }

    /** 切换是否必须先完成一次性导入：自管（{@code SELF}）改为集中（{@code CONCENTRATED}）需要导入。 */
    public static boolean requiresSelfImport(AuthMode from, AuthMode to) {
        return from == AuthMode.SELF && to == AuthMode.CONCENTRATED;
    }

    /**
     * 校验模式切换是否合法。
     *
     * <p>自管改为集中前必须先完成一次性导入（{@code importApplied} 为 {@code true}），否则抛 {@code CONFLICT}。
     */
    public static void assertTransition(AuthMode from, AuthMode to, boolean importApplied) {
        if (requiresSelfImport(from, to) && !importApplied) {
            throw new BizException(ErrorCode.CONFLICT,
                    "自管子系统改为集中授权前必须先完成一次性导入：" + from + " → " + to);
        }
    }
}
