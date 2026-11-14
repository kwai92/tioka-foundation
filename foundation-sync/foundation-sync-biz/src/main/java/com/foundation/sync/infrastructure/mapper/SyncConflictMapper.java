package com.foundation.sync.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.sync.infrastructure.persistence.SyncConflictDO;
import org.apache.ibatis.annotations.Mapper;

/** 同步冲突 Mapper（F-08 BAS-11 §8）。 */
@Mapper
public interface SyncConflictMapper extends BaseMapper<SyncConflictDO> {
}
