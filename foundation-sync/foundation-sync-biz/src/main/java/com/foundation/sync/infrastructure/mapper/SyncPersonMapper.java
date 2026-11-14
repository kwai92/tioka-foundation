package com.foundation.sync.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.sync.infrastructure.persistence.SyncPersonDO;
import org.apache.ibatis.annotations.Mapper;

/** 人员主数据 Mapper（F-08 BAS-11 §7）：同步落地直连 {@code fdn_person}，基础 CRUD 走 {@link BaseMapper}。 */
@Mapper
public interface SyncPersonMapper extends BaseMapper<SyncPersonDO> {
}
