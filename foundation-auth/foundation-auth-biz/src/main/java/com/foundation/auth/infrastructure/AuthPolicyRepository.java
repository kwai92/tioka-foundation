package com.foundation.auth.infrastructure;

import com.foundation.auth.domain.AuthPolicy;
import com.foundation.auth.domain.LoginMode;

import java.util.List;
import java.util.Optional;

/** 登录策略仓储（F-04 BAS-15 §2）。 */
public interface AuthPolicyRepository {

    /** 当前生效的登录策略（单行）。 */
    Optional<AuthPolicy> find();

    /** 保存策略：{@code id} 存在则更新，否则插入。 */
    void save(AuthPolicy policy);

    /** 登录方式（按排序号升序）。 */
    List<LoginMode> listLoginModes();

    /** 按编码查登录方式。 */
    Optional<LoginMode> findLoginMode(String modeCode);

    /** 更新登录方式（启用 / 排序）。 */
    void updateLoginMode(LoginMode mode);
}
