package com.foundation.authz.api;

import java.util.List;

/**
 * 应用目录视图（F-05 BAS-27 §目录）。
 *
 * @param appCode     应用编码
 * @param subsystemId 子系统 ID
 * @param authMode    授权模式
 * @param modules     顶层模块节点
 */
public record DirectoryView(String appCode,
                            String subsystemId,
                            String authMode,
                            List<DirectoryNodeView> modules) {
}
