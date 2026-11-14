package com.foundation.sync.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.sync.infrastructure.persistence.SyncSourceDO;
import org.apache.ibatis.annotations.Mapper;

/** 同步来源 Mapper（F-08 BAS-11 §8）：基础 CRUD 走 {@link BaseMapper}。 */
@Mapper
public interface SyncSourceMapper extends BaseMapper<SyncSourceDO> {
}
