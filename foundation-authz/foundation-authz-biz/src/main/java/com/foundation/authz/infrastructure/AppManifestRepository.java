package com.foundation.authz.infrastructure;

import com.foundation.authz.domain.AppManifest;

import java.util.Optional;

/** 权限清单导入记录仓储（F-05 BAS-05 §清单导入规则）。 */
public interface AppManifestRepository {

    /** 按 {@code (appId, contentHash)} 查已导入记录，命中即幂等返回。 */
    Optional<AppManifest> findByHash(String appId, String contentHash);

    /** 追加一条导入记录（只追加，不更新）。 */
    void insert(AppManifest manifest);
}
