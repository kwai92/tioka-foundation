package com.foundation.authz.api;

import java.util.List;

/**
 * 组织分级管理员范围契约（F-05 BAS-27 §分级授权）。
 */
public interface OrgAdminScopeApi {

    /** 管理员的范围列表（{@code userId} 为空时返回全部）。 */
    List<OrgAdminScopeView> list(String userId);

    /**
     * 新增或更新管理员范围。
     *
     * @param cmd      范围命令
     * @param operator 操作人
     */
    OrgAdminScopeView upsert(OrgAdminScopeCmd cmd, String operator);
}
