package com.foundation.sync.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.sync.infrastructure.persistence.SyncFieldMappingDO;
import org.apache.ibatis.annotations.Mapper;

/** 字段映射 Mapper（F-08 BAS-11 §8）。 */
@Mapper
public interface SyncFieldMappingMapper extends BaseMapper<SyncFieldMappingDO> {
}
