package com.foundation.sequence.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.sequence.infrastructure.persistence.SequenceRuleDO;
import org.apache.ibatis.annotations.Mapper;

/** 编号规则 Mapper（F-09 BAS-18 §18.2）：基础 CRUD 走 {@link BaseMapper}。 */
@Mapper
public interface SequenceRuleMapper extends BaseMapper<SequenceRuleDO> {
}
