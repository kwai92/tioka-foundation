package com.foundation.authz.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.authz.domain.AppManifest;
import com.foundation.authz.infrastructure.mapper.AppManifestMapper;
import com.foundation.authz.infrastructure.persistence.AppManifestDO;

import java.util.Optional;

/** 清单导入记录仓储的 MyBatis-Plus 实现（48-CS §4：application → Repository → Mapper）。 */
public class MybatisAppManifestRepository implements AppManifestRepository {

    private final AppManifestMapper mapper;

    public MybatisAppManifestRepository(AppManifestMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<AppManifest> findByHash(String appId, String contentHash) {
        AppManifestDO row = mapper.selectOne(Wrappers.<AppManifestDO>lambdaQuery()
                .eq(AppManifestDO::getAppId, appId)
                .eq(AppManifestDO::getContentHash, contentHash));
        return Optional.ofNullable(row).map(MybatisAppManifestRepository::toDomain);
    }

    @Override
    public void insert(AppManifest manifest) {
        AppManifestDO row = new AppManifestDO();
        row.setId(manifest.id());
        row.setAppId(manifest.appId());
        row.setVersion(manifest.version());
        row.setContentHash(manifest.contentHash());
        row.setImportedAt(manifest.importedAt());
        row.setImportedBy(manifest.importedBy());
        mapper.insert(row);
    }

    private static AppManifest toDomain(AppManifestDO row) {
        return new AppManifest(row.getId(), row.getAppId(), row.getVersion(), row.getContentHash(),
                row.getImportedAt(), row.getImportedBy());
    }
}
