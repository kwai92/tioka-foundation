package com.foundation.authz.api;

import java.util.List;

/**
 * 角色契约（F-05 BAS-05 §接口 {@code GET/POST/PUT/DELETE /api/roles}）。
 *
 * <p>读 {@code fdn:role:read}，写 {@code fdn:role:admin}；内置角色不可删，仍被授权引用时删除返回 {@code CONFLICT}。
 */
public interface RoleApi {

    /** 角色列表（{@code appCode} 为空时返回全部；含全局角色与内置角色）。 */
    List<RoleView> list(String appCode);

    /** 新建角色（编码全局唯一）。 */
    RoleView create(RoleCmd cmd);

    /** 更新角色名称/数据范围/权限码；编码不可改。 */
    RoleView update(RoleCmd cmd);

    /** 删除角色；内置角色或被授权引用时拒绝。 */
    void delete(String code, String operator);
}
