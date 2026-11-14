package com.foundation.sequence.api;

import java.util.List;

/**
 * 取号结果（F-09 BAS-18 §18.2）：按请求顺序返回渲染后的编号。
 *
 * @param values 编号值列表
 */
public record SequenceBatch(List<String> values) {
}
