package com.foundation.file.domain;

/**
 * 文件状态（F-09 BAS-17、ADR-029 第 13 条删除语义）。
 *
 * <p>有 {@code fdn_file_ref} 引用时禁止物理删除，转 {@link #DELETED} 并延迟清理对象。
 */
public enum FileStatus {

    /** 可用。 */
    ACTIVE,

    /** 已逻辑删除（对象延迟清理）。 */
    DELETED;

    /** 宽松解析；无法识别取 {@link #ACTIVE}（与库默认一致）。 */
    public static FileStatus of(String value) {
        if (value != null) {
            for (FileStatus status : values()) {
                if (status.name().equalsIgnoreCase(value.trim())) {
                    return status;
                }
            }
        }
        return ACTIVE;
    }
}
