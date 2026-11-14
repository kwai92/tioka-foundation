package com.foundation.authz.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.authz.infrastructure.persistence.OrgAdminScopeDO;
import org.apache.ibatis.annotations.Mapper;

/** 组织分级管理员范围 Mapper（简单 CRUD 走 {@link BaseMapper}，48-CS §4）。 */
@Mapper
public interface OrgAdminScopeMapper extends BaseMapper<OrgAdminScopeDO> {
}
