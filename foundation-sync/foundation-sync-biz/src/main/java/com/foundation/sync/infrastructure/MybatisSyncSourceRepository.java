package com.foundation.sync.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.sync.api.dto.SyncSourceStatus;
import com.foundation.sync.domain.SyncSource;
import com.foundation.sync.infrastructure.mapper.SyncSourceMapper;
import com.foundation.sync.infrastructure.persistence.SyncSourceDO;
import com.foundation.sync.spi.SourceCapabilities;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 同步来源仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisSyncSourceRepository implements SyncSourceRepository {

    private final SyncSourceMapper mapper;

    public MybatisSyncSourceRepository(SyncSourceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<SyncSource> list() {
        return mapper.selectList(Wrappers.<SyncSourceDO>lambdaQuery().orderByAsc(SyncSourceDO::getCode))
                .stream().map(MybatisSyncSourceRepository::toDomain).toList();
    }

    @Override
    public Optional<SyncSource> find(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisSyncSourceRepository::toDomain);
    }

    @Override
    public Optional<SyncSource> findByCode(String code) {
        return mapper.selectList(Wrappers.<SyncSourceDO>lambdaQuery().eq(SyncSourceDO::getCode, code))
                .stream().findFirst().map(MybatisSyncSourceRepository::toDomain);
    }

    @Override
    public Optional<SyncSource> findAuthority() {
        return mapper.selectList(Wrappers.<SyncSourceDO>lambdaQuery()
                        .eq(SyncSourceDO::getAuthority, true))
                .stream().findFirst().map(MybatisSyncSourceRepository::toDomain);
    }

    @Override
    public void insert(SyncSource source, String operator) {
        SyncSourceDO row = toDO(source);
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        row.setCreateAt(now);
        row.setUpdateAt(now);
        row.setCreateBy(operator);
        row.setUpdateBy(operator);
        mapper.insert(row);
    }

    @Override
    public void update(SyncSource source, String operator) {
        SyncSourceDO row = toDO(source);
        row.setUpdateAt(LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
        row.setUpdateBy(operator);
        mapper.updateById(row);
    }

    @Override
    public void updateCapabilities(String id, SourceCapabilities capabilities) {
        SyncSourceDO row = new SyncSourceDO();
        row.setId(id);
        row.setCapabilitiesJson(SyncJson.writeCapabilities(capabilities));
        row.setUpdateAt(LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
        mapper.updateById(row);
    }

    private static SyncSourceDO toDO(SyncSource source) {
        SyncSourceDO row = new SyncSourceDO();
        row.setId(source.id());
        row.setCode(source.code());
        row.setName(source.name());
        row.setSourceType(source.sourceType());
        row.setAuthority(source.authority());
        row.setCredentialRef(source.credentialRef());
        row.setAdapterImpl(source.adapterImpl());
        row.setPluginSha256(source.pluginSha256());
        row.setCapabilitiesJson(SyncJson.writeCapabilities(source.capabilities()));
        row.setConfigJson(SyncJson.write(source.config()));
        row.setStatus(source.status() == null ? SyncSourceStatus.ENABLED.name() : source.status().name());
        row.setScheduleCron(source.scheduleCron());
        return row;
    }

    private static SyncSource toDomain(SyncSourceDO row) {
        return new SyncSource(row.getId(), row.getCode(), row.getName(), row.getSourceType(),
                Boolean.TRUE.equals(row.getAuthority()), row.getCredentialRef(), row.getAdapterImpl(),
                row.getPluginSha256(), SyncJson.readCapabilities(row.getCapabilitiesJson()),
                SyncJson.readMap(row.getConfigJson()), SyncSourceStatus.of(row.getStatus()),
                row.getScheduleCron(), row.getUpdateAt() == null ? null
                : row.getUpdateAt().toInstant(ZoneOffset.UTC));
    }
}
