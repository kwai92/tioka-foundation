package com.foundation.org.api.dto;

/**
 * 新建人员（F-03 §5 {@code POST /api/fdn/persons}）。
 *
 * <p>手机与邮箱按明文传入，落库前加密；列表与详情按脱敏开关出参。
 *
 * @param empNo  工号（唯一）
 * @param name   姓名
 * @param type   类型（数字员工经 {@code by-external} 接口维护）
 * @param mobile 手机明文，可空
 * @param email  邮箱明文，可空
 */
public record PersonCreateCmd(String empNo,
                              String name,
                              PersonType type,
                              String mobile,
                              String email) {
}
