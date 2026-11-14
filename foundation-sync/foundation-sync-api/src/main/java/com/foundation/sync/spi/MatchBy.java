package com.foundation.sync.spi;

/**
 * 外部身份候选关联的匹配键（F-08 BAS-11 §2、ADR-026）。
 *
 * <p>只允许工号与已验证邮箱，**不得按姓名匹配**（ADR-052 第 3 条）。
 */
public enum MatchBy {

    /** 工号（{@code emp_no}）。 */
    EMP_NO,

    /** 已验证邮箱。 */
    VERIFIED_EMAIL;

    /** 宽松解析：未知取值返回 {@code null}。 */
    public static MatchBy of(String value) {
        if (value == null) {
            return null;
        }
        for (MatchBy by : values()) {
            if (by.name().equalsIgnoreCase(value.trim())) {
                return by;
            }
        }
        return null;
    }
}
