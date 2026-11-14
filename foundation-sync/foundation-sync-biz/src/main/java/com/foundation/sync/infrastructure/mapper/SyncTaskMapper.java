package com.foundation.sync.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.sync.infrastructure.persistence.SyncTaskDO;
import org.apache.ibatis.annotations.Mapper;

/** 同步任务 Mapper（F-08 BAS-11 §8）：列表分页走 MP {@code PaginationInnerInterceptor}。 */
@Mapper
public interface SyncTaskMapper extends BaseMapper<SyncTaskDO> {
}
