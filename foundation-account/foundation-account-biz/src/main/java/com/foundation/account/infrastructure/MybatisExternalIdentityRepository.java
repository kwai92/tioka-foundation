package com.foundation.account.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.account.api.dto.ExternalIdentitySource;
import com.foundation.account.api.dto.LinkMode;
import com.foundation.account.domain.ExternalIdentity;
import com.foundation.account.infrastructure.mapper.ExternalIdentityMapper;
import com.foundation.account.infrastructure.persistence.ExternalIdentityDO;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 外部身份仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisExternalIdentityRepository implements ExternalIdentityRepository {

    private final ExternalIdentityMapper mapper;

    public MybatisExternalIdentityRepository(ExternalIdentityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ExternalIdentity> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisExternalIdentityRepository::toDomain);
    }

    @Override
    public Optional<ExternalIdentity> findBySourceAndExternalId(ExternalIdentitySource source, String externalId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<ExternalIdentityDO>lambdaQuery()
                .eq(ExternalIdentityDO::getSource, source.name())
                .eq(ExternalIdentityDO::getExternalId, externalId)))
                .map(MybatisExternalIdentityRepository::toDomain);
    }

    @Override
    public List<ExternalIdentity> findPending() {
        return mapper.selectList(Wrappers.<ExternalIdentityDO>lambdaQuery()
                        .isNull(ExternalIdentityDO::getPersonId))
                .stream().map(MybatisExternalIdentityRepository::toDomain).toList();
    }

    @Override
    public List<ExternalIdentity> findByPersonId(String personId) {
        return mapper.selectList(Wrappers.<ExternalIdentityDO>lambdaQuery()
                        .eq(ExternalIdentityDO::getPersonId, personId))
                .stream().map(MybatisExternalIdentityRepository::toDomain).toList();
    }

    @Override
    public void insert(ExternalIdentity identity) {
        mapper.insert(toDO(identity));
    }

    @Override
    public void update(ExternalIdentity identity) {
        mapper.update(null, Wrappers.<ExternalIdentityDO>lambdaUpdate()
                .set(ExternalIdentityDO::getSource, identity.source().name())
                .set(ExternalIdentityDO::getExternalId, identity.externalId())
                .set(ExternalIdentityDO::getPersonId, identity.personId())
                .set(ExternalIdentityDO::getLinkMode, identity.linkMode() == null ? null : identity.linkMode().name())
                .set(ExternalIdentityDO::getLinkedAt, toLocalDateTime(identity.linkedAt()))
                .eq(ExternalIdentityDO::getId, identity.id()));
    }

    private static ExternalIdentityDO toDO(ExternalIdentity identity) {
        ExternalIdentityDO row = new ExternalIdentityDO();
        row.setId(identity.id());
        row.setSource(identity.source().name());
        row.setExternalId(identity.externalId());
        row.setPersonId(identity.personId());
        row.setLinkMode(identity.linkMode() == null ? null : identity.linkMode().name());
        row.setLinkedAt(toLocalDateTime(identity.linkedAt()));
        return row;
    }

    private static ExternalIdentity toDomain(ExternalIdentityDO row) {
        return new ExternalIdentity(row.getId(), ExternalIdentitySource.valueOf(row.getSource()),
                row.getExternalId(), row.getPersonId(),
                row.getLinkMode() == null ? null : LinkMode.valueOf(row.getLinkMode()),
                row.getLinkedAt() == null ? null : row.getLinkedAt().toInstant(ZoneOffset.UTC));
    }

    private static LocalDateTime toLocalDateTime(java.time.Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
