package com.foundation.authz.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.authz.domain.SelfImportKind;
import com.foundation.authz.domain.SelfImportStatus;
import com.foundation.authz.domain.SelfManagedImport;
import com.foundation.authz.domain.SelfManagedImportItem;
import com.foundation.authz.infrastructure.mapper.SelfManagedImportItemMapper;
import com.foundation.authz.infrastructure.mapper.SelfManagedImportMapper;
import com.foundation.authz.infrastructure.persistence.SelfManagedImportDO;
import com.foundation.authz.infrastructure.persistence.SelfManagedImportItemDO;

import java.util.List;
import java.util.Optional;

/** 自管一次性导入仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisSelfManagedImportRepository implements SelfManagedImportRepository {

    private final SelfManagedImportMapper batchMapper;
    private final SelfManagedImportItemMapper itemMapper;

    public MybatisSelfManagedImportRepository(SelfManagedImportMapper batchMapper,
                                              SelfManagedImportItemMapper itemMapper) {
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
    }

    @Override
    public Optional<SelfManagedImport> findById(String id) {
        return Optional.ofNullable(batchMapper.selectById(id)).map(MybatisSelfManagedImportRepository::toBatch);
    }

    @Override
    public List<SelfManagedImport> listBySubsystem(String subsystemId) {
        return batchMapper.selectList(Wrappers.<SelfManagedImportDO>lambdaQuery()
                        .eq(SelfManagedImportDO::getSubsystemId, subsystemId)
                        .orderByAsc(SelfManagedImportDO::getId))
                .stream().map(MybatisSelfManagedImportRepository::toBatch).toList();
    }

    @Override
    public void insert(SelfManagedImport batch) {
        SelfManagedImportDO row = new SelfManagedImportDO();
        row.setId(batch.id());
        row.setSubsystemId(batch.subsystemId());
        row.setStatus(batch.status().name());
        row.setSummary(batch.summary());
        batchMapper.insert(row);
    }

    @Override
    public void update(SelfManagedImport batch) {
        SelfManagedImportDO row = new SelfManagedImportDO();
        row.setId(batch.id());
        row.setStatus(batch.status().name());
        row.setSummary(batch.summary());
        batchMapper.updateById(row);
    }

    @Override
    public void insertItem(SelfManagedImportItem item) {
        SelfManagedImportItemDO row = new SelfManagedImportItemDO();
        row.setId(item.id());
        row.setBatchId(item.batchId());
        row.setKind(item.kind().name());
        row.setExternalKey(item.externalKey());
        row.setPayload(item.payload());
        itemMapper.insert(row);
    }

    @Override
    public List<SelfManagedImportItem> items(String batchId) {
        return itemMapper.selectList(Wrappers.<SelfManagedImportItemDO>lambdaQuery()
                        .eq(SelfManagedImportItemDO::getBatchId, batchId)
                        .orderByAsc(SelfManagedImportItemDO::getKind)
                        .orderByAsc(SelfManagedImportItemDO::getExternalKey))
                .stream().map(MybatisSelfManagedImportRepository::toItem).toList();
    }

    private static SelfManagedImport toBatch(SelfManagedImportDO row) {
        return new SelfManagedImport(row.getId(), row.getSubsystemId(),
                SelfImportStatus.of(row.getStatus()), row.getSummary());
    }

    private static SelfManagedImportItem toItem(SelfManagedImportItemDO row) {
        return new SelfManagedImportItem(row.getId(), row.getBatchId(),
                SelfImportKind.of(row.getKind()), row.getExternalKey(), row.getPayload());
    }
}
