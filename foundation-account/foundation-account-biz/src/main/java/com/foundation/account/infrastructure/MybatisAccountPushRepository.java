package com.foundation.account.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.account.domain.AccountPushRecord;
import com.foundation.account.domain.PushAction;
import com.foundation.account.domain.PushStatus;
import com.foundation.account.infrastructure.mapper.AccountPushMapper;
import com.foundation.account.infrastructure.persistence.AccountPushDO;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 账号待发记录仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisAccountPushRepository implements AccountPushRepository {

    /** 兜底上限，防全表扫描。 */
    private static final int MAX_LIMIT = 500;

    private final AccountPushMapper mapper;

    public MybatisAccountPushRepository(AccountPushMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(AccountPushRecord record) {
        mapper.insert(toDO(record));
    }

    @Override
    public List<AccountPushRecord> findDue(Instant now, int limit) {
        LocalDateTime nowLocal = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        int capped = Math.max(1, Math.min(limit, MAX_LIMIT));
        return mapper.selectList(Wrappers.<AccountPushDO>lambdaQuery()
                        .eq(AccountPushDO::getStatus, PushStatus.PENDING.name())
                        .and(w -> w.isNull(AccountPushDO::getNextRetryAt)
                                .or().le(AccountPushDO::getNextRetryAt, nowLocal))
                        .orderByAsc(AccountPushDO::getCreateAt)
                        .last("LIMIT " + capped))
                .stream().map(MybatisAccountPushRepository::toDomain).toList();
    }

    @Override
    public long countPending() {
        return mapper.selectCount(Wrappers.<AccountPushDO>lambdaQuery()
                .eq(AccountPushDO::getStatus, PushStatus.PENDING.name()));
    }

    @Override
    public void markSent(String id) {
        mapper.update(null, Wrappers.<AccountPushDO>lambdaUpdate()
                .set(AccountPushDO::getStatus, PushStatus.SENT.name())
                .set(AccountPushDO::getLastError, null)
                .set(AccountPushDO::getNextRetryAt, null)
                .setSql("attempts = attempts + 1")
                .eq(AccountPushDO::getId, id));
    }

    @Override
    public void markFailed(String id, String error, Instant nextRetryAt) {
        mapper.update(null, Wrappers.<AccountPushDO>lambdaUpdate()
                .set(AccountPushDO::getStatus, nextRetryAt == null ? PushStatus.FAILED.name() : PushStatus.PENDING.name())
                .set(AccountPushDO::getLastError, error)
                .set(AccountPushDO::getNextRetryAt, toLocalDateTime(nextRetryAt))
                .setSql("attempts = attempts + 1")
                .eq(AccountPushDO::getId, id));
    }

    private static AccountPushDO toDO(AccountPushRecord record) {
        AccountPushDO row = new AccountPushDO();
        row.setId(record.id());
        row.setAccountId(record.accountId());
        row.setAction(record.action().name());
        row.setPayloadJson(record.payloadJson());
        row.setStatus(record.status().name());
        row.setAttempts(record.attempts());
        row.setLastError(record.lastError());
        row.setNextRetryAt(toLocalDateTime(record.nextRetryAt()));
        return row;
    }

    private static AccountPushRecord toDomain(AccountPushDO row) {
        return new AccountPushRecord(row.getId(), row.getAccountId(), PushAction.valueOf(row.getAction()),
                row.getPayloadJson(), PushStatus.valueOf(row.getStatus()),
                row.getAttempts() == null ? 0 : row.getAttempts(), row.getLastError(),
                row.getNextRetryAt() == null ? null : row.getNextRetryAt().toInstant(ZoneOffset.UTC));
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
