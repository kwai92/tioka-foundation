package com.foundation.file.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/**
 * 存储后端类型（ADR-026 的 {@code ObjectStorageAdapter} 可切后端）。
 */
public enum StorageKind {

    /** 本地磁盘（单机 / 开发与回归默认）。 */
    LOCAL,

    /** S3 兼容对象存储（MinIO / 客户 OSS）。 */
    S3;

    /** 严格解析：无法识别时按 {@code INTERNAL} 口径抛参数错误（写库口径不允许含糊）。 */
    public static StorageKind of(String value) {
        if (value != null) {
            for (StorageKind kind : values()) {
                if (kind.name().equalsIgnoreCase(value.trim())) {
                    return kind;
                }
            }
        }
        throw new BizException(ErrorCode.INTERNAL, "未知存储后端：" + value);
    }
}
