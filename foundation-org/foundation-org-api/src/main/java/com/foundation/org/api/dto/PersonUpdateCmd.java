package com.foundation.org.api.dto;

/**
 * 修改人员（F-03 §5 {@code PUT /api/fdn/persons/{id}}）。
 *
 * @param name   姓名
 * @param type   类型
 * @param mobile 手机明文；{@code null} 表示不变
 * @param email  邮箱明文；{@code null} 表示不变
 */
public record PersonUpdateCmd(String name,
                              PersonType type,
                              String mobile,
                              String email) {
}
