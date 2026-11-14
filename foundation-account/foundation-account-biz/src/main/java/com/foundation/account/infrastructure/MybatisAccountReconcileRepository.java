package com.foundation.account.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.account.domain.AccountReconcileRecord;
import com.foundation.account.infrastructure.mapper.AccountReconcileMapper;
import com.foundation.account.infrastructure.persistence.AccountReconcileDO;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 每日对账记录仓储的 MyBatis-Plus 实现（48-CS §4）：只追加。 */
public class MybatisAccountReconcileRepository implements AccountReconcileRepository {

    private final AccountReconcileMapper mapper;

    public MybatisAccountReconcileRepository(AccountReconcileMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(AccountReconcileRecord record) {
        AccountReconcileDO row = new AccountReconcileDO();
        row.setId(record.id());
        row.setRanAt(LocalDateTime.ofInstant(record.ranAt(), ZoneOffset.UTC));
        row.setTotal(record.total());
        row.setDrifted(record.drifted());
        row.setFixed(record.fixed());
        row.setHeld(record.held());
        row.setDetailJson(record.detailJson());
        row.setCreateAt(LocalDateTime.now(ZoneOffset.UTC));
        mapper.insert(row);
    }

    @Override
    public List<AccountReconcileRecord> recent(int limit) {
        int capped = Math.max(1, limit);
        return mapper.selectList(Wrappers.<AccountReconcileDO>lambdaQuery()
                        .orderByDesc(AccountReconcileDO::getRanAt)
                        .last("LIMIT " + capped))
                .stream().map(MybatisAccountReconcileRepository::toDomain).toList();
    }

    private static AccountReconcileRecord toDomain(AccountReconcileDO row) {
        return new AccountReconcileRecord(row.getId(), row.getRanAt().toInstant(ZoneOffset.UTC),
                row.getTotal() == null ? 0 : row.getTotal(),
                row.getDrifted() == null ? 0 : row.getDrifted(),
                row.getFixed() == null ? 0 : row.getFixed(),
                Boolean.TRUE.equals(row.getHeld()),
                row.getDetailJson());
    }
}
