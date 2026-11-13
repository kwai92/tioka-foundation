package com.foundation.org.domain;

import com.foundation.org.api.dto.ChangeAction;
import com.foundation.org.api.dto.ChangeEntityType;
import com.foundation.org.api.dto.OrgChangeView;

import java.time.Instant;

/**
 * 变更历史条目（F-03 §4.3）：只追加，含 {@code before_json} / {@code after_json} 字段白名单摘要。
 *
 * @param id         主键（{@code chg_…}）
 * @param entityType 对象类型
 * @param entityId   对象 ID
 * @param action     动作
 * @param beforeJson 变更前摘要，可空
 * @param afterJson  变更后摘要，可空
 * @param source     来源
 * @param actor      操作人
 * @param createAt   发生时间
 */
public record OrgChangeLogEntry(String id,
                                ChangeEntityType entityType,
                                String entityId,
                                ChangeAction action,
                                String beforeJson,
                                String afterJson,
                                String source,
                                String actor,
                                Instant createAt) {

    /** 转对外视图。 */
    public OrgChangeView toView() {
        return new OrgChangeView(id, entityType, entityId, action, beforeJson, afterJson, source,
                actor, createAt);
    }
}
