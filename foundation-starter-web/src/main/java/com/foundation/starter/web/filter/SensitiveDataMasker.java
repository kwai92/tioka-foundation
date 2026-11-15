package com.foundation.starter.web.filter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 敏感数据脱敏工具（F-02 BAS-24 §foundation-starter-web）。
 *
 * <p>无状态工具类，供请求日志使用：把 query 串与请求头中命中敏感键的值替换为 {@code ***}。
 * 命中判定为“键名包含敏感键”，忽略大小写（例如 {@code client_secret} 命中 {@code client-secret}）。
 */
public final class SensitiveDataMasker {

    private static final String MASK = "***";

    private SensitiveDataMasker() {
    }

    /**
     * 脱敏 {@code k=v&k2=v2} 形式的查询串；敏感键的值替换为 {@code ***}，其余原样保留。
     *
     * @param rawQuery      原始查询串（不含前导 {@code ?}），可为 null
     * @param sensitiveKeys 敏感键清单，可为 null
     * @return 脱敏后的查询串
     */
    public static String maskQueryString(String rawQuery, List<String> sensitiveKeys) {
        if (rawQuery == null || rawQuery.isEmpty() || sensitiveKeys == null || sensitiveKeys.isEmpty()) {
            return rawQuery;
        }
        String[] pairs = rawQuery.split("&", -1);
        String[] masked = new String[pairs.length];
        for (int i = 0; i < pairs.length; i++) {
            masked[i] = maskPair(pairs[i], sensitiveKeys);
        }
        return String.join("&", masked);
    }

    /**
     * 脱敏请求头：敏感头的值替换为 {@code ***}；多值以 {@code ", "} 连接；null 头跳过。
     *
     * @param headers       请求头（名 → 多值），可为 null
     * @param sensitiveKeys 敏感键清单，可为 null
     * @return 脱敏后的单值头映射（保持插入顺序）
     */
    public static Map<String, String> maskHeaders(Map<String, List<String>> headers, List<String> sensitiveKeys) {
        Map<String, String> masked = new LinkedHashMap<>();
        if (headers == null) {
            return masked;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String name = entry.getKey();
            if (name == null) {
                continue;
            }
            if (isSensitive(name, sensitiveKeys)) {
                masked.put(name, MASK);
                continue;
            }
            List<String> values = entry.getValue();
            masked.put(name, values == null ? "" : String.join(", ", values));
        }
        return masked;
    }

    private static String maskPair(String pair, List<String> sensitiveKeys) {
        int eq = pair.indexOf('=');
        if (eq < 0) {
            return pair;
        }
        String key = pair.substring(0, eq);
        if (isSensitive(key, sensitiveKeys)) {
            return key + "=" + MASK;
        }
        return pair;
    }

    private static boolean isSensitive(String name, List<String> sensitiveKeys) {
        if (name == null || sensitiveKeys == null) {
            return false;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        for (String key : sensitiveKeys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            if (normalized.contains(key.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
