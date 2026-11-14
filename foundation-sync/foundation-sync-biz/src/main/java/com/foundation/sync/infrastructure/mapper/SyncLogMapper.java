package com.foundation.sync.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.sync.infrastructure.persistence.SyncLogDO;
import org.apache.ibatis.annotations.Mapper;

/** 同步日志 Mapper（F-08 BAS-11 §8）：只追加。 */
@Mapper
public interface SyncLogMapper extends BaseMapper<SyncLogDO> {
}
