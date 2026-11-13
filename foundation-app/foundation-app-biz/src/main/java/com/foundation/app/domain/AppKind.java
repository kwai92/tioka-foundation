package com.foundation.app.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.util.Set;

/**
 * 应用类型（F-05 BAS-04 §领域模型）：{@code PORTAL} 门户、{@code ADMIN} 管理台、{@code SUB} 子系统。
 *
 * <p>Foundation 门户与管理台本身也登记为应用，保证菜单、角色、权限码口径统一。
 */
public final class AppKind {

    public static final String PORTAL = "PORTAL";
    public static final String ADMIN = "ADMIN";
    public static final String SUB = "SUB";

    private static final Set<String> SUPPORTED = Set.of(PORTAL, ADMIN, SUB);

    private AppKind() {
    }

    /** 校验并归一化应用类型；为空取 {@code SUB}。 */
    public static String of(String raw) {
        if (raw == null || raw.isBlank()) {
            return SUB;
        }
        String normalized = raw.trim().toUpperCase();
        if (!SUPPORTED.contains(normalized)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "应用类型只能是 PORTAL/ADMIN/SUB：" + raw);
        }
        return normalized;
    }
}
