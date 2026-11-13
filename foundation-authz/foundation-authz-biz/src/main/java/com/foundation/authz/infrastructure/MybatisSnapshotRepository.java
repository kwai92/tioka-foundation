package com.foundation.authz.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.authz.domain.AuthzIdPrefix;
import com.foundation.authz.domain.AuthzSnapshot;
import com.foundation.authz.infrastructure.mapper.AuthzSnapshotMapper;
import com.foundation.authz.infrastructure.persistence.AuthzSnapshotDO;
import com.foundation.common.id.Ids;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 授权快照仓储的 MyBatis-Plus 实现（48-CS §4）；{@code body} 用 {@link ObjectMapper} 编解码。 */
public class MybatisSnapshotRepository implements SnapshotRepository {

    private final AuthzSnapshotMapper mapper;
    private final ObjectMapper json;

    public MybatisSnapshotRepository(AuthzSnapshotMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    public Optional<Long> version(String personId, String appCode) {
        AuthzSnapshotDO row = findRow(personId, appCode);
        return Optional.ofNullable(row).map(AuthzSnapshotDO::getVersion);
    }

    @Override
    public Optional<AuthzSnapshot> find(String personId, String appCode) {
        AuthzSnapshotDO row = findRow(personId, appCode);
        if (row == null || row.getBody() == null || row.getBody().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(readBody(row.getBody()));
    }

    @Override
    public List<String> listPersonsByApp(String appCode) {
        return mapper.selectList(Wrappers.<AuthzSnapshotDO>lambdaQuery()
                        .eq(AuthzSnapshotDO::getAppCode, appCode))
                .stream().map(AuthzSnapshotDO::getPersonId).toList();
    }

    @Override
    public void upsert(AuthzSnapshot snapshot) {
        AuthzSnapshotDO existing = findRow(snapshot.personId(), snapshot.appCode());
        LocalDateTime computedAt = LocalDateTime.ofInstant(snapshot.computedAt(), ZoneOffset.UTC);
        if (existing == null) {
            AuthzSnapshotDO row = new AuthzSnapshotDO();
            row.setId(Ids.newId(AuthzIdPrefix.SNAPSHOT));
            row.setPersonId(snapshot.personId());
            row.setAppCode(snapshot.appCode());
            row.setVersion(snapshot.version());
            row.setBody(writeBody(snapshot));
            row.setComputedAt(computedAt);
            mapper.insert(row);
            return;
        }
        AuthzSnapshotDO row = new AuthzSnapshotDO();
        row.setId(existing.getId());
        row.setVersion(snapshot.version());
        row.setBody(writeBody(snapshot));
        row.setComputedAt(computedAt);
        mapper.updateById(row);
    }

    private AuthzSnapshotDO findRow(String personId, String appCode) {
        return mapper.selectOne(Wrappers.<AuthzSnapshotDO>lambdaQuery()
                .eq(AuthzSnapshotDO::getPersonId, personId)
                .eq(AuthzSnapshotDO::getAppCode, appCode));
    }

    private String writeBody(AuthzSnapshot snapshot) {
        try {
            return json.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("授权快照序列化失败：" + snapshot.personId() + "/" + snapshot.appCode(), ex);
        }
    }

    private AuthzSnapshot readBody(String body) {
        try {
            return json.readValue(body, AuthzSnapshot.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("授权快照反序列化失败", ex);
        }
    }
}
