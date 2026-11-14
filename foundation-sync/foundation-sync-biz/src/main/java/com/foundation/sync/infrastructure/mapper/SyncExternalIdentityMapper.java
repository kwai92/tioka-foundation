package com.foundation.sync.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.sync.infrastructure.persistence.SyncExternalIdentityDO;
import org.apache.ibatis.annotations.Mapper;

/** 外部身份 Mapper（F-08 BAS-11 §4）：非权威来源的落地范围被限制为 {@code fdn_external_identity}。 */
@Mapper
public interface SyncExternalIdentityMapper extends BaseMapper<SyncExternalIdentityDO> {
}
