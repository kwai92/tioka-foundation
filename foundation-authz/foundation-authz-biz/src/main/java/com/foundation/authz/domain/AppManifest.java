package com.foundation.authz.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 权限清单导入记录（F-05 BAS-05 §领域模型）：只追加，同哈希不重复导入、版本只能前进。
 *
 * @param id          记录 ID（{@code mft_{ulid}}）
 * @param appId       所属应用
 * @param version     清单版本
 * @param contentHash 规范化后内容的 SHA-256
 * @param importedAt  导入时间
 * @param importedBy  导入人
 */
public record AppManifest(String id,
                          String appId,
                          long version,
                          String contentHash,
                          Instant importedAt,
                          String importedBy) {

    public AppManifest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(contentHash, "contentHash");
    }
}
