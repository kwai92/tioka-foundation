package com.foundation.sync.infrastructure;

import com.foundation.sync.domain.FieldMapping;

import java.util.List;

/** 字段映射仓储（F-08 BAS-11 §8 {@code fdn_sync_field_mapping}）。 */
public interface SyncFieldMappingRepository {

    List<FieldMapping> listBySource(String sourceId);

    /** 整包替换某来源的全部映射（先删后插，同事务）。 */
    void replaceAll(String sourceId, List<FieldMapping> mappings);
}
