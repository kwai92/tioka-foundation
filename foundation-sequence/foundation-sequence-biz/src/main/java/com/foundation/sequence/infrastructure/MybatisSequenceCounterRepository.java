package com.foundation.sequence.infrastructure;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.sequence.infrastructure.mapper.SequenceMapper;

/** 序列取号仓储的注解 SQL 实现（48-CS §4：{@code fdn_sequence} 复合主键，不用 MyBatis-Plus 单列主键）。 */
public class MybatisSequenceCounterRepository implements SequenceCounterRepository {

    private final SequenceMapper mapper;

    public MybatisSequenceCounterRepository(SequenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long next(String ruleCode, String periodKey, long step) {
        Long value = mapper.nextValue(ruleCode, periodKey, step);
        if (value == null) {
            throw new BizException(ErrorCode.INTERNAL, "取号失败：" + ruleCode);
        }
        return value;
    }

    @Override
    public long current(String ruleCode, String periodKey) {
        Long value = mapper.currentValue(ruleCode, periodKey);
        return value == null ? 0L : value;
    }
}
