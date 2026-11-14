package com.foundation.sync.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.sync.infrastructure.persistence.SyncPositionDO;
import org.apache.ibatis.annotations.Mapper;

/** 岗位主数据 Mapper（F-08 BAS-11 §7）：同步落地直连 {@code fdn_position}。 */
@Mapper
public interface SyncPositionMapper extends BaseMapper<SyncPositionDO> {
}
