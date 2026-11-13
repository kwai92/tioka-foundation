package com.foundation.authz.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.authz.infrastructure.persistence.RoleMutexDO;
import org.apache.ibatis.annotations.Mapper;

/** 互斥角色 Mapper（简单 CRUD 走 {@link BaseMapper}，48-CS §4）。 */
@Mapper
public interface RoleMutexMapper extends BaseMapper<RoleMutexDO> {
}
