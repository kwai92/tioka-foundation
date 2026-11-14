package com.foundation.sequence.api;

/**
 * 当前周期已用值（F-09 BAS-18 §18.2，排障用）。
 *
 * @param ruleCode  规则业务码值
 * @param periodKey 周期键：{@code -} / {@code yyyyMMdd} / {@code yyyyMM} / {@code yyyy}
 * @param current   当前已用值（无记录时 0）
 */
public record SequenceCurrentView(String ruleCode, String periodKey, long current) {
}
