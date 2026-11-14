package com.foundation.starter.client.sequence;

/**
 * 编号取号（F-09 BAS-18 §18.2）：供应用生成单据号、合同号等；以数据库为单一权威，允许跳号。
 */
public interface SequenceClient {

    /**
     * 取号。
     *
     * @param ruleCode       规则业务码值
     * @param count          取号个数（至多 1000）
     * @param idempotencyKey 幂等键；非空时同一键在 TTL 内重复调用返回同一批值
     */
    SequenceValues next(String ruleCode, int count, String idempotencyKey);

    /** 当前周期已用值（排障）。 */
    SequenceCurrent current(String ruleCode);
}
