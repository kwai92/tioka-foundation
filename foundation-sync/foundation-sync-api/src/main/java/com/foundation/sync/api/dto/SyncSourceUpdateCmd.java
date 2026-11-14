package com.foundation.sync.api.dto;

import java.util.Map;

/**
 * 修改同步来源（F-08 BAS-11 §10 {@code PUT /api/sync/sources/{id}}）。
 *
 * <p>字段为 {@code null} 表示不修改；权威来源切换是显式管理动作（停旧、启新、跑一次全量对账，
 * ADR-052 第 4 条）。
 *
 * @param name          名称
 * @param authority     是否权威来源
 * @param credentialRef 凭证引用
 * @param adapterImpl   适配器类名
 * @param config        来源私有配置（整包替换）
 * @param status        启用 / 停用
 * @param scheduleCron  定时计划
 */
public record SyncSourceUpdateCmd(String name,
                                  Boolean authority,
                                  String credentialRef,
                                  String adapterImpl,
                                  Map<String, Object> config,
                                  SyncSourceStatus status,
                                  String scheduleCron) {
}
