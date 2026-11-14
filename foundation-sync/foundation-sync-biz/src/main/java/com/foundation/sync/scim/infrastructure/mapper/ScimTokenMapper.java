package com.foundation.sync.scim.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.sync.scim.infrastructure.persistence.ScimTokenDO;
import org.apache.ibatis.annotations.Mapper;

/** SCIM 令牌 Mapper（F-08 BAS-13 §5）。 */
@Mapper
public interface ScimTokenMapper extends BaseMapper<ScimTokenDO> {
}
