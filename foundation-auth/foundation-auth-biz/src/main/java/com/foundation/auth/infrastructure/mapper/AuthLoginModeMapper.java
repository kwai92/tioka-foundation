package com.foundation.auth.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.auth.infrastructure.persistence.AuthLoginModeDO;
import org.apache.ibatis.annotations.Mapper;

/** 登录方式 Mapper（F-04 BAS-15 §2）：基础 CRUD 走 {@link BaseMapper}。 */
@Mapper
public interface AuthLoginModeMapper extends BaseMapper<AuthLoginModeDO> {
}
