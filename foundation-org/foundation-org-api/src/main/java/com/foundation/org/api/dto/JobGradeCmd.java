package com.foundation.org.api.dto;

/**
 * 职级写入（F-03 §5 {@code GET/POST /api/fdn/job-grades}、{@code PUT/{id}}）。
 *
 * @param code       编码（唯一）
 * @param name       名称
 * @param series     序列，如 M / P
 * @param gradeLevel 等级
 * @param sortNo     排序，空取 0
 */
public record JobGradeCmd(String code,
                          String name,
                          String series,
                          Integer gradeLevel,
                          Integer sortNo) {
}
