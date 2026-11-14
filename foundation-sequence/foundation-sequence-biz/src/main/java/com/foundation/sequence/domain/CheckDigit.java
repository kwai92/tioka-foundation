package com.foundation.sequence.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/**
 * 校验位算法（F-09 BAS-18 §18.2）：对已渲染的编号体计算校验字符。
 *
 * <p>{@code MOD10}（Luhn）与 {@code MOD11}（加权 2..7 循环）只统计数字字符，非数字忽略；
 * {@code MOD11} 得 10 时以 {@code X} 表示。
 */
public enum CheckDigit {

    /** 无校验位。 */
    NONE {
        @Override
        public String compute(String payload) {
            return "";
        }
    },

    /** Luhn（MOD10）。 */
    MOD10 {
        @Override
        public String compute(String payload) {
            int sum = 0;
            boolean doubled = true;
            for (int i = payload.length() - 1; i >= 0; i--) {
                int digit = Character.digit(payload.charAt(i), 10);
                if (digit < 0) {
                    continue;
                }
                if (doubled) {
                    digit *= 2;
                    if (digit > 9) {
                        digit -= 9;
                    }
                }
                sum += digit;
                doubled = !doubled;
            }
            return Integer.toString((10 - sum % 10) % 10);
        }
    },

    /** 加权 MOD11（权重 2..7 自右向左循环）。 */
    MOD11 {
        @Override
        public String compute(String payload) {
            int[] weights = {2, 3, 4, 5, 6, 7};
            int sum = 0;
            int wi = 0;
            for (int i = payload.length() - 1; i >= 0; i--) {
                int digit = Character.digit(payload.charAt(i), 10);
                if (digit < 0) {
                    continue;
                }
                sum += digit * weights[wi % weights.length];
                wi++;
            }
            int check = (11 - sum % 11) % 11;
            return check == 10 ? "X" : Integer.toString(check);
        }
    };

    /** 对编号体计算校验字符。 */
    public abstract String compute(String payload);

    /** 严格解析：无法识别时抛参数错误。 */
    public static CheckDigit of(String value) {
        if (value != null) {
            String normalized = value.trim();
            for (CheckDigit digit : values()) {
                if (digit.name().equalsIgnoreCase(normalized)) {
                    return digit;
                }
            }
        }
        throw new BizException(ErrorCode.BAD_REQUEST, "未知校验位算法：" + value);
    }
}
