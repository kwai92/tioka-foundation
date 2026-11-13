package com.foundation.org.api.dto;

import java.time.Instant;

/**
 * 变更历史视图（F-03 §4.3）。
 *
 * @param id         变更记录 ID（{@code chg_…}）
 * @param entityType 对象类型
 * @param entityId   对象 ID
 * @param action     动作
 * @param beforeJson 变更前字段白名单摘要，可空
 * @param afterJson  变更后字段白名单摘要，可空
 * @param source     来源
 * @param actor      操作人
 * @param createAt   发生时间
 */
public record OrgChangeView(String id,
                            ChangeEntityType entityType,
                            String entityId,
                            ChangeAction action,
                            String beforeJson,
                            String afterJson,
                            String source,
                            String actor,
                            Instant createAt) {
}
