package com.foundation.sequence.api;

/**
 * 编号规则视图（F-09 BAS-18 §18.2 数据模型 {@code fdn_sequence_rule}）。
 *
 * @param id         规则 ID（{@code sqr_…}）
 * @param code       业务码值（全局唯一，对应字典 {@code sequence_biz_code}）
 * @param name       名称
 * @param pattern    渲染模式，如 {@code {prefix}{date:yyyyMMdd}{seq:4}{check}}
 * @param prefix     前缀
 * @param seqLength  流水位宽（补零到该位）
 * @param step       步长
 * @param resetCycle 重置周期：{@code NONE} / {@code DAILY} / {@code MONTHLY} / {@code YEARLY}
 * @param checkDigit 校验位算法：{@code NONE} / {@code MOD10} / {@code MOD11}
 * @param status     状态：{@code ACTIVE} / {@code INACTIVE}
 * @param sample     预览样例
 */
public record SequenceRuleView(String id,
                               String code,
                               String name,
                               String pattern,
                               String prefix,
                               int seqLength,
                               int step,
                               String resetCycle,
                               String checkDigit,
                               String status,
                               String sample) {
}
