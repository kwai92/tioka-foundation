package com.foundation.org.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.org.infrastructure.persistence.OrgUnitDO;
import org.apache.ibatis.annotations.Mapper;

/** 组织单元 Mapper（简单 CRUD 走 {@link BaseMapper}，48-CS §4）。 */
@Mapper
public interface OrgUnitMapper extends BaseMapper<OrgUnitDO> {
}
