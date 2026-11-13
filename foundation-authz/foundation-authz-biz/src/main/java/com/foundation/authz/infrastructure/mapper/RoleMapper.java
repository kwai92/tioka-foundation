package com.foundation.authz.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.authz.infrastructure.persistence.RoleDO;
import org.apache.ibatis.annotations.Mapper;

/** 角色 Mapper（简单 CRUD 走 {@link BaseMapper}，48-CS §4）。 */
@Mapper
public interface RoleMapper extends BaseMapper<RoleDO> {
}
