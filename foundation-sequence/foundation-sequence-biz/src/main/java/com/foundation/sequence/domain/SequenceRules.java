package com.foundation.sequence.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/**
 * 编号规则领域规则（F-09 BAS-18 §18.2）：入参规范化与不变量校验。
 *
 * <p>不依赖 Web / 安全 / 持久化上下文，供应用服务与控制器共用。
 */
public final class SequenceRules {

    /** 业务码值最大长度（列宽 {@code VARCHAR(64)}）。 */
    public static final int MAX_CODE = 64;

    /** 名称最大长度（列宽 {@code VARCHAR(255)}）。 */
    public static final int MAX_NAME = 255;

    /** 渲染模式最大长度（列宽 {@code VARCHAR(255)}）。 */
    public static final int MAX_PATTERN = 255;

    /** 前缀最大长度（列宽 {@code VARCHAR(32)}）。 */
    public static final int MAX_PREFIX = 32;

    /** 预览样例最大长度（列宽 {@code VARCHAR(64)}）。 */
    public static final int MAX_SAMPLE = 64;

    /** 流水位宽上限。 */
    public static final int MAX_SEQ_LENGTH = 18;

    /** 流水位宽缺省值。 */
    public static final int DEFAULT_SEQ_LENGTH = 4;

    /** 步长缺省值。 */
    public static final int DEFAULT_STEP = 1;

    private SequenceRules() {
    }

    /** 业务码值必填，返回去空白后的值。 */
    public static String requireCode(String code) {
        return requireText(code, "业务码值", MAX_CODE);
    }

    /** 名称必填。 */
    public static String requireName(String name) {
        return requireText(name, "名称", MAX_NAME);
    }

    /** 渲染模式必填。 */
    public static String requirePattern(String pattern) {
        return requireText(pattern, "渲染模式", MAX_PATTERN);
    }

    /** 前缀规范化：空 → {@code null}。 */
    public static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        String trimmed = prefix.trim();
        if (trimmed.length() > MAX_PREFIX) {
            throw new BizException(ErrorCode.BAD_REQUEST, "前缀超长（至多 " + MAX_PREFIX + " 字符）");
        }
        return trimmed;
    }

    /** 流水位宽：缺省 {@value #DEFAULT_SEQ_LENGTH}，取值 {@code [1, MAX_SEQ_LENGTH]}。 */
    public static int seqLength(Integer seqLength) {
        int value = seqLength == null ? DEFAULT_SEQ_LENGTH : seqLength;
        if (value < 1 || value > MAX_SEQ_LENGTH) {
            throw new BizException(ErrorCode.BAD_REQUEST, "流水位宽超出范围：" + value);
        }
        return value;
    }

    /** 步长：缺省 {@value #DEFAULT_STEP}，至少为 1。 */
    public static int step(Integer step) {
        int value = step == null ? DEFAULT_STEP : step;
        if (value < 1) {
            throw new BizException(ErrorCode.BAD_REQUEST, "步长必须为正：" + value);
        }
        return value;
    }

    /** 预览样例截断到列宽。 */
    public static String normalizeSample(String sample) {
        if (sample == null) {
            return null;
        }
        return sample.length() > MAX_SAMPLE ? sample.substring(0, MAX_SAMPLE) : sample;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, field + "不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BizException(ErrorCode.BAD_REQUEST, field + "超长（至多 " + maxLength + " 字符）");
        }
        return trimmed;
    }
}
