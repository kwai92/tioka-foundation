package com.foundation.sequence.api;

/**
 * 取号命令（F-09 BAS-18 §18.2 {@code POST /api/fdn/sequences:next}）。
 *
 * @param ruleCode 规则业务码值
 * @param count    取号个数，缺省 1
 */
public record NextSequenceCmd(String ruleCode, Integer count) {
}
