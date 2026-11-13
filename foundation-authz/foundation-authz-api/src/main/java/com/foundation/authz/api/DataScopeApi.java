package com.foundation.authz.api;

import java.time.Instant;
import java.util.List;

/**
 * 数据权限契约（F-06 BAS-06 §接口 {@code /api/fdn}）。
 *
 * <p>只在 Foundation 定义与计算，不执行；执行在应用内由 BAS-08 的 starter 承载。
 */
public interface DataScopeApi {

    /** 设置角色数据范围（部门档位 + 各维度取值），覆盖式写入。 */
    void setRoleDataScope(String roleCode, RoleDataScopeCmd cmd);

    /** 读回角色数据范围。 */
    RoleDataScopeView roleDataScope(String roleCode);

    /**
     * 人员维度授予（不走角色的授予），返回新增授权 ID 列表。
     *
     * <p>{@code source} 为 {@code RULE}/{@code SYNC} 时不覆盖同维度已有的 {@code MANUAL} 授予。
     */
    List<String> grantPersonDimension(String personId, PersonDimensionCmd cmd);

    /** 撤销人员维度授予（不动角色授予）。 */
    void revokePersonDimension(String personId, String dimensionCode);

    /**
     * 计算人员在指定应用、指定时刻的最终数据范围。
     *
     * <p>= 有效角色（BAS-05）对应的部门范围与各维度取值 ∪ 人员授予，跨角色取并集。
     */
    DataScopeView resolve(String personId, String appCode, Instant at);
}
