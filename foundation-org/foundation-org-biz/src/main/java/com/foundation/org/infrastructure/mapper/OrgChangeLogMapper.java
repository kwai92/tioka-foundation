package com.foundation.org.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.org.infrastructure.persistence.OrgChangeLogDO;
import org.apache.ibatis.annotations.Mapper;

/** 变更历史 Mapper（只追加，无逻辑删）。 */
@Mapper
public interface OrgChangeLogMapper extends BaseMapper<OrgChangeLogDO> {
}
