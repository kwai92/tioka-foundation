package com.foundation.sync.scim.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.sync.scim.domain.ScimToken;
import com.foundation.sync.scim.domain.ScimTokenStatus;
import com.foundation.sync.scim.infrastructure.mapper.ScimTokenMapper;
import com.foundation.sync.scim.infrastructure.persistence.ScimTokenDO;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** SCIM 令牌仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisScimTokenRepository implements ScimTokenRepository {

    private final ScimTokenMapper mapper;

    public MybatisScimTokenRepository(ScimTokenMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(ScimToken token) {
        ScimTokenDO row = new ScimTokenDO();
        row.setId(token.id());
        row.setName(token.name());
        row.setTokenHash(token.tokenHash());
        row.setPrefix(token.prefix());
        row.setScopes(token.scopes());
        row.setStatus(token.status().name());
        row.setLastUsedAt(toLocal(token.lastUsedAt()));
        row.setExpiresAt(toLocal(token.expiresAt()));
        row.setCreateAt(toLocal(token.createAt() == null ? Instant.now() : token.createAt()));
        mapper.insert(row);
    }

    @Override
    public List<ScimToken> list() {
        return mapper.selectList(Wrappers.<ScimTokenDO>lambdaQuery()
                        .orderByDesc(ScimTokenDO::getCreateAt)).stream()
                .map(MybatisScimTokenRepository::toDomain).toList();
    }

    @Override
    public Optional<ScimToken> find(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisScimTokenRepository::toDomain);
    }

    @Override
    public Optional<ScimToken> findByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(Wrappers.<ScimTokenDO>lambdaQuery()
                .eq(ScimTokenDO::getPrefix, prefix))).map(MybatisScimTokenRepository::toDomain);
    }

    @Override
    public void touchLastUsed(String id, Instant at) {
        ScimTokenDO row = new ScimTokenDO();
        row.setId(id);
        row.setLastUsedAt(toLocal(at));
        mapper.updateById(row);
    }

    @Override
    public void delete(String id) {
        mapper.deleteById(id);
    }

    private static ScimToken toDomain(ScimTokenDO row) {
        return new ScimToken(row.getId(), row.getName(), row.getTokenHash(), row.getPrefix(),
                row.getScopes(), ScimTokenStatus.of(row.getStatus()),
                toInstant(row.getLastUsedAt()), toInstant(row.getExpiresAt()), toInstant(row.getCreateAt()));
    }

    private static LocalDateTime toLocal(Instant at) {
        return at == null ? null : LocalDateTime.ofInstant(at, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime at) {
        return at == null ? null : at.toInstant(ZoneOffset.UTC);
    }
}
