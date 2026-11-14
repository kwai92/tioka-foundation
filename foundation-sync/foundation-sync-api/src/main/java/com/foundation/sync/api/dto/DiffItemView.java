package com.foundation.sync.api.dto;

import com.foundation.sync.spi.SyncObjectType;

import java.util.Map;

/**
 * 差异条目视图（F-08 BAS-11 §6 预演明细）。
 *
 * @param objectType        对象类型
 * @param externalId        来源侧标识
 * @param kind              差异类别
 * @param fields            内部字段取值（映射后），仅 CREATE / UPDATE 有意义
 * @param parentExternalId  上级对象外部 ID，仅 MOVE 有意义
 * @param lockedFields      被本地锁定、本次跳过的字段
 */
public record DiffItemView(SyncObjectType objectType,
                           String externalId,
                           DiffKind kind,
                           Map<String, Object> fields,
                           String parentExternalId,
                           java.util.Set<String> lockedFields) {
}
