package com.foundation.starter.security.principal;

/**
 * 登录身份（F-07 §2）：**只带身份、不带权限、不带 tenantId**（S15 §9.2、ADR-051 第 3 条）。
 *
 * <p>角色与权限一律来自授权快照（F-07 §4）；本记录仅承载令牌声明与主组织单元编码。
 *
 * @param userId         应用内用户 / 账号 ID（Tioka 为十进制字符串，ADR-029 第 31 条）
 * @param personId       Foundation 人员 ID（{@code per_…}）
 * @param accountId      Foundation 账号 ID（{@code acc_…}）
 * @param employeeNo     工号
 * @param displayName    显示名
 * @param primaryOrgUnit 主组织单元编码
 * @param appCode        应用编码
 */
public record Principal(String userId,
                        String personId,
                        String accountId,
                        String employeeNo,
                        String displayName,
                        String primaryOrgUnit,
                        String appCode) {
}
