package com.foundation.authz.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.authz.infrastructure.persistence.RolePermissionDO;
import org.apache.ibatis.annotations.Mapper;

/** 角色-权限码关联 Mapper（按角色整体重写，48-CS §4）。 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermissionDO> {
}
