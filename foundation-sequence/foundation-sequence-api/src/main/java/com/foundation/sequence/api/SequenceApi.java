package com.foundation.sequence.api;

/**
 * 编号取号契约（F-09 BAS-18 §18.2）：按规则生成业务编号，保证并发唯一。
 *
 * <p>供各应用生成单据号、合同号等；取号以数据库为单一权威（原子自增），允许跳号。
 */
public interface SequenceApi {

    /**
     * 取号。
     *
     * @param ruleCode       规则业务码值
     * @param count          取号个数（至多 1000）
     * @param idempotencyKey 幂等键；非空时同一键在 TTL 内重复调用返回同一批值
     */
    SequenceBatch next(String ruleCode, int count, String idempotencyKey);

    /** 当前周期已用值（排障）。 */
    SequenceCurrentView current(String ruleCode);
}
