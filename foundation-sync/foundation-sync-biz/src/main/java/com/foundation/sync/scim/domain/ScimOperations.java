package com.foundation.sync.scim.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** SCIM {@code PATCH} 请求体的 {@code Operations} 解析（RFC 7644 §3.5.2）。 */
public final class ScimOperations {

    private ScimOperations() {
    }

    /** 解析 {@code Operations}（大小写不敏感）；缺失即拒绝。 */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> of(Map<String, Object> body) {
        if (body == null) {
            return List.of();
        }
        Object raw = body.get("Operations") != null ? body.get("Operations") : body.get("operations");
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> operations = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    operations.add((Map<String, Object>) map);
                }
            }
            return operations;
        }
        throw new BizException(ErrorCode.BAD_REQUEST, "PATCH 缺少 Operations");
    }
}
