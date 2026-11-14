package com.foundation.sync.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.common.id.Ids;
import com.foundation.sync.domain.FieldMapping;
import com.foundation.sync.domain.SyncIdPrefix;
import com.foundation.sync.infrastructure.mapper.SyncFieldMappingMapper;
import com.foundation.sync.infrastructure.persistence.SyncFieldMappingDO;
import com.foundation.sync.spi.SyncObjectType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** 字段映射仓储的 MyBatis-Plus 实现。 */
public class MybatisSyncFieldMappingRepository implements SyncFieldMappingRepository {

    private final SyncFieldMappingMapper mapper;

    public MybatisSyncFieldMappingRepository(SyncFieldMappingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<FieldMapping> listBySource(String sourceId) {
        return mapper.selectList(Wrappers.<SyncFieldMappingDO>lambdaQuery()
                        .eq(SyncFieldMappingDO::getSourceId, sourceId)
                        .orderByAsc(SyncFieldMappingDO::getObjectType)
                        .orderByAsc(SyncFieldMappingDO::getExternalField))
                .stream().map(MybatisSyncFieldMappingRepository::toDomain).toList();
    }

    @Override
    public void replaceAll(String sourceId, List<FieldMapping> mappings) {
        mapper.delete(Wrappers.<SyncFieldMappingDO>lambdaQuery().eq(SyncFieldMappingDO::getSourceId, sourceId));
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        for (FieldMapping mapping : mappings == null ? List.<FieldMapping>of() : mappings) {
            SyncFieldMappingDO row = new SyncFieldMappingDO();
            row.setId(mapping.id() == null ? Ids.newId(SyncIdPrefix.FIELD_MAPPING) : mapping.id());
            row.setSourceId(sourceId);
            row.setObjectType(mapping.objectType().name());
            row.setExternalField(mapping.externalField());
            row.setInternalField(mapping.internalField());
            row.setDefaultValue(mapping.defaultValue());
            row.setTransform(mapping.transform());
            row.setRequired(mapping.required());
            row.setCreateAt(now);
            row.setUpdateAt(now);
            mapper.insert(row);
        }
    }

    private static FieldMapping toDomain(SyncFieldMappingDO row) {
        return new FieldMapping(row.getId(), row.getSourceId(), SyncObjectType.of(row.getObjectType()),
                row.getExternalField(), row.getInternalField(), row.getDefaultValue(), row.getTransform(),
                Boolean.TRUE.equals(row.getRequired()));
    }
}
