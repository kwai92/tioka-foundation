package com.foundation.authz.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.authz.infrastructure.persistence.AuthzSnapshotDO;
import org.apache.ibatis.annotations.Mapper;

/** 授权快照 Mapper（F-06 BAS-07 §数据模型）。 */
@Mapper
public interface AuthzSnapshotMapper extends BaseMapper<AuthzSnapshotDO> {
}
