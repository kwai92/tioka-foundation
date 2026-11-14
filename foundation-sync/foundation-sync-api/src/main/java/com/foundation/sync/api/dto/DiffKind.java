package com.foundation.sync.api.dto;

/**
 * 差异条目类别（F-08 BAS-11 §6）。
 *
 * <p>预演与落地使用同一差异算法，口径一致。
 */
public enum DiffKind {

    /** 来源侧新增，本地不存在。 */
    CREATE,

    /** 本地已存在且字段有变化。 */
    UPDATE,

    /** 本地存在但来源侧已删除 / 本次全量快照中消失。 */
    DISABLE,

    /** 组织单元换上级（{@code path} / {@code depth} 级联更新）。 */
    MOVE

}
