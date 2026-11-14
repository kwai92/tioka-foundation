package com.foundation.sequence.infrastructure;

import com.foundation.sequence.domain.SequenceRule;

import java.util.List;
import java.util.Optional;

/** 编号规则仓储（F-09 BAS-18 §18.2）：持久化边界，实现走 MyBatis-Plus（48-CS §4）。 */
public interface SequenceRuleRepository {

    /** 全部规则（按业务码值升序）。 */
    List<SequenceRule> list();

    /** 按业务码值读取规则。 */
    Optional<SequenceRule> findByCode(String code);

    /** 追加一条规则。 */
    void insert(SequenceRule rule);

    /** 更新规则（按 ID）。 */
    void update(SequenceRule rule);
}
