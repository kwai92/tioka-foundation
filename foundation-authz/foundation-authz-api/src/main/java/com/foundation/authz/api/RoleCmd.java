package com.foundation.authz.api;

import java.util.List;

/**
 * 角色命令（F-05 BAS-05 §接口）。
 *
 * @param code        角色编码（主键，创建必填、更新时不可改）
 * @param name        角色名称
 * @param scope       {@code SYSTEM} 全局 / {@code APP} 某应用
 * @param appId       {@code scope=APP} 时必填
 * @param dataScope   数据范围（{@code ALL}/{@code DEPTS}/{@code DEPT}/{@code DEPT_AND_CHILD}/{@code SELF}/{@code CUSTOM}）
 * @param permissions 权限码集合
 * @param operator    操作人
 */
public record RoleCmd(String code,
                      String name,
                      String scope,
                      String appId,
                      String dataScope,
                      List<String> permissions,
                      String operator) {
}
