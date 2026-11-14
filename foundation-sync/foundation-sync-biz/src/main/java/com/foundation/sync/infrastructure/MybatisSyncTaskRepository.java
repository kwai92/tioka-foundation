package com.foundation.sync.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foundation.sync.api.dto.SyncMode;
import com.foundation.sync.api.dto.SyncPauseReason;
import com.foundation.sync.api.dto.SyncTaskStatus;
import com.foundation.sync.api.dto.SyncTriggerMode;
import com.foundation.sync.domain.SyncTask;
import com.foundation.sync.infrastructure.mapper.SyncTaskMapper;
import com.foundation.sync.infrastructure.persistence.SyncTaskDO;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 同步任务仓储的 MyBatis-Plus 实现（分页走 {@code PaginationInnerInterceptor}）。 */
public class MybatisSyncTaskRepository implements SyncTaskRepository {

    private final SyncTaskMapper mapper;

    public MybatisSyncTaskRepository(SyncTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(SyncTask task) {
        mapper.insert(toDO(task));
    }

    @Override
    public void update(SyncTask task) {
        mapper.updateById(toDO(task));
    }

    @Override
    public Optional<SyncTask> find(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisSyncTaskRepository::toDomain);
    }

    @Override
    public List<SyncTask> page(String sourceId, SyncTaskStatus status, Instant from, Instant to,
                               int page, int size) {
        Page<SyncTaskDO> result = mapper.selectPage(new Page<>(page, size), wrapper(sourceId, status, from, to)
                .orderByDesc(SyncTaskDO::getStartedAt));
        return result.getRecords().stream().map(MybatisSyncTaskRepository::toDomain).toList();
    }

    @Override
    public long count(String sourceId, SyncTaskStatus status, Instant from, Instant to) {
        Long total = mapper.selectCount(wrapper(sourceId, status, from, to));
        return total == null ? 0L : total;
    }

    private static LambdaQueryWrapper<SyncTaskDO> wrapper(String sourceId, SyncTaskStatus status,
                                                          Instant from, Instant to) {
        LambdaQueryWrapper<SyncTaskDO> wrapper = Wrappers.<SyncTaskDO>lambdaQuery();
        if (sourceId != null && !sourceId.isBlank()) {
            wrapper.eq(SyncTaskDO::getSourceId, sourceId);
        }
        if (status != null) {
            wrapper.eq(SyncTaskDO::getStatus, status.name());
        }
        if (from != null) {
            wrapper.ge(SyncTaskDO::getStartedAt, toUtc(from));
        }
        if (to != null) {
            wrapper.le(SyncTaskDO::getStartedAt, toUtc(to));
        }
        return wrapper;
    }

    private static SyncTaskDO toDO(SyncTask task) {
        SyncTaskDO row = new SyncTaskDO();
        row.setId(task.id());
        row.setSourceId(task.sourceId());
        row.setMode(task.mode() == null ? null : task.mode().name());
        row.setTriggerMode(task.triggerMode() == null ? null : task.triggerMode().name());
        row.setStatus(task.status() == null ? null : task.status().name());
        row.setDryRun(task.dryRun());
        row.setCursorJson(SyncJson.writeCursor(task.cursor()));
        row.setCreatedCount(task.createdCount());
        row.setUpdatedCount(task.updatedCount());
        row.setDisabledCount(task.disabledCount());
        row.setMovedCount(task.movedCount());
        row.setCapabilityMissing(task.capabilityMissing());
        row.setPauseReason(task.pauseReason() == null ? null : task.pauseReason().name());
        row.setStartedAt(toUtc(task.startedAt()));
        row.setFinishedAt(toUtc(task.finishedAt()));
        row.setCreateBy(task.createBy());
        return row;
    }

    private static SyncTask toDomain(SyncTaskDO row) {
        return new SyncTask(row.getId(), row.getSourceId(), SyncMode.of(row.getMode()),
                SyncTriggerMode.of(row.getTriggerMode()), SyncTaskStatus.of(row.getStatus()),
                Boolean.TRUE.equals(row.getDryRun()), SyncJson.readCursor(row.getCursorJson()),
                intOf(row.getCreatedCount()), intOf(row.getUpdatedCount()), intOf(row.getDisabledCount()),
                intOf(row.getMovedCount()), Boolean.TRUE.equals(row.getCapabilityMissing()),
                SyncPauseReason.of(row.getPauseReason()), toInstant(row.getStartedAt()),
                toInstant(row.getFinishedAt()), row.getCreateBy());
    }

    private static int intOf(Integer value) {
        return value == null ? 0 : value;
    }

    private static LocalDateTime toUtc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
