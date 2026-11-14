package com.foundation.sync.domain;

import com.foundation.sync.spi.SyncObjectType;

/**
 * 主数据实际变更（F-08 BAS-11 §10）：用于复用 {@code fdn.org.changed} / {@code fdn.person.*} 事件。
 *
 * @param objectType 对象类型
 * @param localId    本地主键（{@code org_…} / {@code per_…}）
 * @param version    行版本（每次写 +1）
 * @param changeType 变化类型（{@code CREATED} / {@code UPDATED} / {@code MOVED} / {@code DISABLED}）
 */
public record LandedChange(SyncObjectType objectType, String localId, long version, String changeType) {

    /** 新增。 */
    public static final String CREATED = "CREATED";
    /** 修改。 */
    public static final String UPDATED = "UPDATED";
    /** 移动。 */
    public static final String MOVED = "MOVED";
    /** 停用。 */
    public static final String DISABLED = "DISABLED";
}
