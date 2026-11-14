package com.foundation.sync.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.sync.api.dto.SyncAction;
import com.foundation.sync.api.dto.SyncLogLevel;
import com.foundation.sync.domain.SyncLog;
import com.foundation.sync.infrastructure.mapper.SyncLogMapper;
import com.foundation.sync.infrastructure.persistence.SyncLogDO;
import com.foundation.sync.spi.SyncObjectType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 同步日志仓储的 MyBatis-Plus 实现（只追加）。 */
public class MybatisSyncLogRepository implements SyncLogRepository {

    private final SyncLogMapper mapper;

    public MybatisSyncLogRepository(SyncLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void appendAll(List<SyncLog> logs) {
        for (SyncLog log : logs == null ? List.<SyncLog>of() : logs) {
            SyncLogDO row = new SyncLogDO();
            row.setId(log.id());
            row.setTaskId(log.taskId());
            row.setLevel(log.level() == null ? SyncLogLevel.INFO.name() : log.level().name());
            row.setObjectType(log.objectType() == null ? null : log.objectType().name());
            row.setExternalId(log.externalId());
            row.setAction(log.action() == null ? null : log.action().name());
            row.setMessage(log.message());
            row.setCreateAt(toUtc(log.createAt()));
            mapper.insert(row);
        }
    }

    @Override
    public List<SyncLog> listByTask(String taskId) {
        return mapper.selectList(Wrappers.<SyncLogDO>lambdaQuery()
                        .eq(SyncLogDO::getTaskId, taskId)
                        .orderByAsc(SyncLogDO::getCreateAt))
                .stream().map(MybatisSyncLogRepository::toDomain).toList();
    }

    private static SyncLog toDomain(SyncLogDO row) {
        return new SyncLog(row.getId(), row.getTaskId(), SyncLogLevel.of(row.getLevel()),
                SyncObjectType.of(row.getObjectType()), row.getExternalId(), SyncAction.of(row.getAction()),
                row.getMessage(), toInstant(row.getCreateAt()));
    }

    private static LocalDateTime toUtc(Instant instant) {
        return instant == null ? LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
                : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
