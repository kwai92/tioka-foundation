package com.foundation.sequence.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 编号渲染器（F-09 BAS-18 §18.2）：按 {@code pattern} 生成业务编号。
 *
 * <p>占位符：{@code {prefix}}、{@code {date:yyyyMMdd}}（{@code {date}} 等价 {@code yyyyMMdd}）、
 * {@code {seq:N}}（补零到 N 位；{@code {seq}} 用规则位宽）、{@code {check}}（校验位）。
 * 校验位对「去掉 {@code {check}} 后的编号体」计算，非数字字符忽略。
 */
public final class SequenceGenerator {

    private SequenceGenerator() {
    }

    /** 按规则渲染编号：{@code sequence} 为流水值，{@code date} 为日期占位符取值。 */
    public static String render(SequenceRule rule, long sequence, LocalDate date) {
        String body = substitute(rule, sequence, date, null);
        String check = rule.checkDigit().compute(body);
        return substitute(rule, sequence, date, check);
    }

    /** 预览样例：以流水值 1 渲染。 */
    public static String sample(SequenceRule rule, LocalDate date) {
        return render(rule, 1L, date);
    }

    private static String substitute(SequenceRule rule, long sequence, LocalDate date, String checkValue) {
        String pattern = rule.pattern();
        StringBuilder out = new StringBuilder(pattern.length() + 16);
        int cursor = 0;
        while (cursor < pattern.length()) {
            int open = pattern.indexOf('{', cursor);
            if (open < 0) {
                out.append(pattern, cursor, pattern.length());
                break;
            }
            out.append(pattern, cursor, open);
            int close = pattern.indexOf('}', open + 1);
            if (close < 0) {
                throw new BizException(ErrorCode.BAD_REQUEST, "编号模式占位符未闭合：" + pattern);
            }
            out.append(resolve(pattern.substring(open + 1, close).trim(), rule, sequence, date, checkValue));
            cursor = close + 1;
        }
        return out.toString();
    }

    private static String resolve(String token, SequenceRule rule, long sequence, LocalDate date, String checkValue) {
        if (token.equals("prefix")) {
            return rule.prefix() == null ? "" : rule.prefix();
        }
        if (token.equals("check")) {
            return checkValue == null ? "" : checkValue;
        }
        if (token.equals("seq")) {
            return pad(sequence, rule.seqLength());
        }
        if (token.startsWith("seq:")) {
            return pad(sequence, width(token.substring(4)));
        }
        if (token.equals("date") || token.startsWith("date:")) {
            String format = token.length() > 5 ? token.substring(5) : "yyyyMMdd";
            try {
                return DateTimeFormatter.ofPattern(format).format(date);
            } catch (IllegalArgumentException ex) {
                throw new BizException(ErrorCode.BAD_REQUEST, "日期格式非法：" + format);
            }
        }
        throw new BizException(ErrorCode.BAD_REQUEST, "未知编号占位符：{" + token + "}");
    }

    private static int width(String text) {
        try {
            int value = Integer.parseInt(text.trim());
            if (value < 1) {
                throw new NumberFormatException(text);
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "流水位宽非法：" + text);
        }
    }

    private static String pad(long value, int width) {
        String digits = Long.toString(value);
        if (width <= digits.length()) {
            return digits;
        }
        return "0".repeat(width - digits.length()) + digits;
    }
}
