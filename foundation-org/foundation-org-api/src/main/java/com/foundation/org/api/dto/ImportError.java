package com.foundation.org.api.dto;

/**
 * 导入逐行错误（F-03 §4.1）：整表先全量校验，任一行失败则整体拒绝并返回逐行错误。
 *
 * @param sheet   工作表名（组织单元 / 岗位 / 人员 / 任职）
 * @param row     工作表内行号（含表头，从 1 起）
 * @param message 错误说明
 */
public record ImportError(String sheet, int row, String message) {
}
