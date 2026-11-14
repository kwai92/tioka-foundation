package com.foundation.sequence.domain;

import java.time.LocalDateTime;

/**
 * 编号规则（{@code fdn_sequence_rule} 一行的领域视图，F-09 BAS-18 §18.2）。
 *
 * @param id         规则 ID（{@code sqr_…}）
 * @param code       业务码值（全局唯一）
 * @param name       名称
 * @param pattern    渲染模式
 * @param prefix     前缀，可空
 * @param seqLength  流水位宽
 * @param step       步长
 * @param resetCycle 重置周期
 * @param checkDigit 校验位算法
 * @param status     状态
 * @param sample     预览样例，可空
 * @param createAt   创建时间
 * @param updateAt   更新时间
 */
public record SequenceRule(String id,
                           String code,
                           String name,
                           String pattern,
                           String prefix,
                           int seqLength,
                           int step,
                           ResetCycle resetCycle,
                           CheckDigit checkDigit,
                           SequenceStatus status,
                           String sample,
                           LocalDateTime createAt,
                           LocalDateTime updateAt) {

    /** 以本规则副本替换预览样例。 */
    public SequenceRule withSample(String newSample) {
        return new SequenceRule(id, code, name, pattern, prefix, seqLength, step, resetCycle,
                checkDigit, status, newSample, createAt, updateAt);
    }
}
