package com.foundation.account.domain;

import com.foundation.account.api.dto.AccountStatus;
import com.foundation.account.api.dto.AccountType;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/**
 * 账号规则（F-04 §1；单测 {@code AccountRulesTest}）。
 *
 * <p>约束与 {@code ck_account_*}、{@code uk_account_person_personal} 一致，在应用层先行拦截。
 */
public final class AccountRules {

    private AccountRules() {
    }

    /**
     * 类型与人员绑定约束：个人账号必关联人员；应急管理员与服务账号不关联人员。
     *
     * <p>对应 {@code ck_account_emergency_no_person CHECK (type = 'PERSONAL' OR person_id IS NULL)}。
     */
    public static void requirePersonBinding(AccountType type, String personId) {
        if (type == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "账号类型必填");
        }
        if (type == AccountType.PERSONAL) {
            if (personId == null || personId.isBlank()) {
                throw new BizException(ErrorCode.BAD_REQUEST, "个人账号必须关联人员");
            }
            return;
        }
        if (personId != null && !personId.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非个人账号不允许关联人员：" + type);
        }
    }

    /** 一个人员最多一个个人账号（S15 §8.2）。 */
    public static void requireNoExistingPersonal(boolean personalExists) {
        if (personalExists) {
            throw new BizException(ErrorCode.CONFLICT, "该人员已存在个人账号");
        }
    }

    /** 登录名必填且格式合法。 */
    public static void requireLoginName(String loginName) {
        if (loginName == null || loginName.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "登录名必填");
        }
        if (!LoginNameRule.isValid(loginName)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "登录名格式不合法：" + loginName);
        }
    }

    /**
     * 开通初始状态：**应急管理员默认停用**（F-04 §1，启用需双人确认）；
     * 个人账号（F-04 §1）与服务账号（F-04 §3）开通即可用，为 {@code NORMAL}。
     */
    public static AccountStatus initialStatus(AccountType type) {
        return type == AccountType.EMERGENCY_ADMIN ? AccountStatus.DISABLED : AccountStatus.NORMAL;
    }

    /** 停用仅对未停用账号生效（幂等）：已停用返回 {@code false}。 */
    public static boolean canDisable(AccountStatus status) {
        return status != AccountStatus.DISABLED;
    }

    /** 锁定仅对 {@code NORMAL} 账号生效。 */
    public static boolean canLock(AccountStatus status) {
        return status == AccountStatus.NORMAL;
    }

    /** 解锁仅对 {@code LOCKED} 账号生效。 */
    public static boolean canUnlock(AccountStatus status) {
        return status == AccountStatus.LOCKED;
    }
}
