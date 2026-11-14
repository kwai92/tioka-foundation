package com.foundation.sequence.infrastructure;

/**
 * 序列取号仓储（F-09 BAS-18 §18.2）：累加并返回当前值，以数据库为单一权威。
 *
 * <p>实现直接用一条原子语句完成，多实例天然唯一；允许跳号（不为回滚补号）。
 */
public interface SequenceCounterRepository {

    /**
     * 以 {@code step} 原子递增某规则在某周期的当前值，并返回递增后的当前值。
     *
     * @return 递增后的当前值（首次调用无行时为 {@code step}）
     */
    long next(String ruleCode, String periodKey, long step);

    /** 当前周期已用值；无记录返回 {@code 0}。 */
    long current(String ruleCode, String periodKey);
}
