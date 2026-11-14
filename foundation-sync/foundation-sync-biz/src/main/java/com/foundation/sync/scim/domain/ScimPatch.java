package com.foundation.sync.scim.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SCIM 2.0 PATCH 局部更新（F-08 BAS-13 §1，RFC 7644 §3.5.2）。
 *
 * <p>只支持 {@code add} / {@code replace} / {@code remove} 三类操作与顶层或点号路径
 * （如 {@code active}、{@code name.givenName}）；无 {@code path} 时按对象值整体合并。
 * 不支持的 {@code op} 一律返回 {@code 400}（**不静默忽略**）。
 */
public final class ScimPatch {

    private ScimPatch() {
    }

    /** 在载荷 Map 上原地应用一组操作。 */
    public static void apply(Map<String, Object> payload, List<Map<String, Object>> operations) {
        if (operations == null || operations.isEmpty()) {
            return;
        }
        for (Map<String, Object> operation : operations) {
            if (operation == null) {
                continue;
            }
            String op = lower(ScimValues.text(operation.get("op")));
            Object value = operation.get("value");
            String path = ScimValues.text(operation.get("path"));
            if (path == null || path.isBlank()) {
                if (value instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        payload.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    continue;
                }
                throw new BizException(ErrorCode.BAD_REQUEST, "PATCH 无 path 时 value 必须是对象");
            }
            switch (op) {
                case "add", "replace" -> setPath(payload, path, value);
                case "remove" -> payload.remove(path);
                default -> throw new BizException(ErrorCode.BAD_REQUEST, "不支持的 PATCH op：" + operation.get("op"));
            }
        }
    }

    /** 写入（点号路径会逐层创建子对象）。 */
    private static void setPath(Map<String, Object> payload, String path, Object value) {
        String[] segments = path.split("\\.");
        Map<String, Object> current = payload;
        for (int i = 0; i < segments.length - 1; i++) {
            Object child = current.get(segments[i]);
            if (child instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) map;
                current = nested;
            } else {
                Map<String, Object> created = new LinkedHashMap<>();
                current.put(segments[i], created);
                current = created;
            }
        }
        current.put(segments[segments.length - 1], value);
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
