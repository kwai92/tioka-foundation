package com.foundation.sync.api.dto;

import java.util.Map;

/**
 * 新建同步来源（F-08 BAS-11 §10 {@code POST /api/sync/sources}，需 {@code Idempotency-Key}）。
 *
 * @param code          来源编码（全局唯一）
 * @param name          名称
 * @param sourceType    来源类型（如 {@code FEISHU} / {@code LDAP}）
 * @param authority     是否权威来源；为 {@code true} 时须保证部署内唯一
 * @param credentialRef 凭证引用（ADR-042，只存引用）
 * @param adapterImpl   适配器类名（{@code SyncSourceAdapter} 实现）
 * @param config        来源私有配置（不含密钥），可空
 * @param scheduleCron  定时计划，可空
 */
public record SyncSourceCreateCmd(String code,
                                  String name,
                                  String sourceType,
                                  Boolean authority,
                                  String credentialRef,
                                  String adapterImpl,
                                  Map<String, Object> config,
                                  String scheduleCron) {
}
