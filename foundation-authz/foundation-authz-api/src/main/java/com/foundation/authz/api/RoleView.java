package com.foundation.authz.api;

import java.util.List;

/**
 * 角色视图（F-05 BAS-05 §领域模型）。
 *
 * @param code        角色编码
 * @param name        名称
 * @param scope       {@code SYSTEM} 全局 / {@code APP} 某应用
 * @param appId       {@code scope=APP} 时非空
 * @param builtIn     内置标志（内置角色不可删）
 * @param dataScope   数据范围
 * @param permissions 权限码集合
 */
public record RoleView(String code,
                       String name,
                       String scope,
                       String appId,
                       boolean builtIn,
                       String dataScope,
                       List<String> permissions) {
}
