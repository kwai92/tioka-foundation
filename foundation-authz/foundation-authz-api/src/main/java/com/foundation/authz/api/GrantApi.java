package com.foundation.authz.api;

import java.util.List;
import java.util.Set;

/**
 * 授权契约（F-05 BAS-05 §接口 {@code GET/POST/DELETE /api/grants}）。
 *
 * <p>权限码 {@code fdn:grant:admin}（HIGH）；授予高风险角色需二次确认并审计（BAS-19）。
 */
public interface GrantApi {

    /** 授权列表（按授予对象过滤；两个条件均为 {@code null} 时返回全部）。 */
    List<RoleGrantView> list(String subjectType, String subjectId);

    /** 新增授权，返回授权 ID（{@code grt_{ulid}}）。 */
    String grant(RoleGrantCmd cmd);

    /** 撤销授权。 */
    void revoke(String grantId, String operator);

    /**
     * 计算人员在指定应用、指定时刻的有效角色集合。
     *
     * <p>= 直接授予 ∪ 组织单元（含子树）授予 ∪ 岗位授予，再过滤生效期与互斥角色。
     */
    Set<String> effectiveRoles(String personId, String appCode, java.time.Instant at);
}
