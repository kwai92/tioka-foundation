package com.foundation.auth.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.auth.infrastructure.persistence.AuthPolicyDO;
import org.apache.ibatis.annotations.Mapper;

/** 登录策略 Mapper（F-04 BAS-15 §2）：基础 CRUD 走 {@link BaseMapper}。 */
@Mapper
public interface AuthPolicyMapper extends BaseMapper<AuthPolicyDO> {
}
