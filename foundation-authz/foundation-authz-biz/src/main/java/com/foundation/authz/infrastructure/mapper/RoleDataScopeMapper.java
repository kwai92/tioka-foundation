package com.foundation.authz.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.authz.infrastructure.persistence.RoleDataScopeDO;
import org.apache.ibatis.annotations.Mapper;

/** 角色数据范围 Mapper（简单 CRUD 走 {@link BaseMapper}，48-CS §4）。 */
@Mapper
public interface RoleDataScopeMapper extends BaseMapper<RoleDataScopeDO> {
}
