package com.foundation.sync.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.sync.infrastructure.persistence.SyncOrgUnitDO;
import org.apache.ibatis.annotations.Mapper;

/** 组织单元主数据 Mapper（F-08 BAS-11 §7）：同步落地直连 {@code fdn_org_unit}。 */
@Mapper
public interface SyncOrgUnitMapper extends BaseMapper<SyncOrgUnitDO> {
}
