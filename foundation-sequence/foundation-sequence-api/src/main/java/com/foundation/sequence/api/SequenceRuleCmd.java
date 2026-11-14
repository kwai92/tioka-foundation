package com.foundation.sequence.api;

/**
 * 新建 / 修改编号规则的命令（F-09 BAS-18 §18.2）。
 *
 * @param code       业务码值（全局唯一）
 * @param name       名称
 * @param pattern    渲染模式
 * @param prefix     前缀，可空
 * @param seqLength  流水位宽，缺省 4
 * @param step       步长，缺省 1
 * @param resetCycle 重置周期，缺省 {@code NONE}
 * @param checkDigit 校验位算法，缺省 {@code NONE}
 * @param status     状态，缺省 {@code ACTIVE}
 */
public record SequenceRuleCmd(String code,
                              String name,
                              String pattern,
                              String prefix,
                              Integer seqLength,
                              Integer step,
                              String resetCycle,
                              String checkDigit,
                              String status) {
}
