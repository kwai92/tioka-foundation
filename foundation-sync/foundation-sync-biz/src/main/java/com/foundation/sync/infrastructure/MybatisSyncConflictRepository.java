package com.foundation.sync.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foundation.sync.api.dto.SyncConflictType;
import com.foundation.sync.domain.SyncConflict;
import com.foundation.sync.infrastructure.mapper.SyncConflictMapper;
import com.foundation.sync.infrastructure.persistence.SyncConflictDO;
import com.foundation.sync.spi.SyncObjectType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 同步冲突仓储的 MyBatis-Plus 实现。 */
public class MybatisSyncConflictRepository implements SyncConflictRepository {

    private final SyncConflictMapper mapper;

    public MybatisSyncConflictRepository(SyncConflictMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertAll(List<SyncConflict> conflicts) {
        for (SyncConflict conflict : conflicts == null ? List.<SyncConflict>of() : conflicts) {
            SyncConflictDO row = new SyncConflictDO();
            row.setId(conflict.id());
            row.setTaskId(conflict.taskId());
            row.setSourceId(conflict.sourceId());
            row.setObjectType(conflict.objectType() == null ? null : conflict.objectType().name());
            row.setExternalId(conflict.externalId());
            row.setConflictType(conflict.conflictType().name());
            row.setDetailJson(SyncJson.write(conflict.detail()));
            row.setResolved(conflict.resolved());
            row.setResolvedBy(conflict.resolvedBy());
            row.setCreateAt(conflict.createAt() == null ? LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
                    : LocalDateTime.ofInstant(conflict.createAt(), ZoneOffset.UTC));
            mapper.insert(row);
        }
    }

    @Override
    public Optional<SyncConflict> find(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisSyncConflictRepository::toDomain);
    }

    @Override
    public void markResolved(String id, String operator) {
        SyncConflictDO row = new SyncConflictDO();
        row.setId(id);
        row.setResolved(true);
        row.setResolvedBy(operator);
        mapper.updateById(row);
    }

    @Override
    public List<SyncConflict> page(Boolean resolved, int page, int size) {
        Page<SyncConflictDO> result = mapper.selectPage(new Page<>(page, size), wrapper(resolved)
                .orderByDesc(SyncConflictDO::getCreateAt));
        return result.getRecords().stream().map(MybatisSyncConflictRepository::toDomain).toList();
    }

    @Override
    public long count(Boolean resolved) {
        Long total = mapper.selectCount(wrapper(resolved));
        return total == null ? 0L : total;
    }

    private static LambdaQueryWrapper<SyncConflictDO> wrapper(Boolean resolved) {
        LambdaQueryWrapper<SyncConflictDO> wrapper = Wrappers.lambdaQuery();
        if (resolved != null) {
            wrapper.eq(SyncConflictDO::getResolved, resolved);
        }
        return wrapper;
    }

    private static SyncConflict toDomain(SyncConflictDO row) {
        return new SyncConflict(row.getId(), row.getTaskId(), row.getSourceId(),
                SyncObjectType.of(row.getObjectType()), row.getExternalId(),
                SyncConflictType.of(row.getConflictType()), SyncJson.readMap(row.getDetailJson()),
                Boolean.TRUE.equals(row.getResolved()), row.getResolvedBy(),
                row.getCreateAt() == null ? null : row.getCreateAt().toInstant(ZoneOffset.UTC));
    }
}
