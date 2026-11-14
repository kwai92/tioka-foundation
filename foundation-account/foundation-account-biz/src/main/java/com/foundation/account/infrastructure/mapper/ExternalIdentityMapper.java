package com.foundation.account.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.account.infrastructure.persistence.ExternalIdentityDO;
import org.apache.ibatis.annotations.Mapper;

/** 外部身份 Mapper（简单 CRUD 走 {@link BaseMapper}，48-CS §4）。 */
@Mapper
public interface ExternalIdentityMapper extends BaseMapper<ExternalIdentityDO> {
}
