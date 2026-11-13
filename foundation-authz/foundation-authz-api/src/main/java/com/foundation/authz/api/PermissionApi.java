package com.foundation.authz.api;

import java.util.List;

/**
 * 权限码目录契约（F-05 BAS-05 §接口 {@code GET /api/permissions}）。
 *
 * <p>对登录用户开放但按应用过滤：只返回用户有权应用内的权限码，避免全域权限码泄露。
 */
public interface PermissionApi {

    /** 应用可用权限码目录（{@code appCode} 为空时返回全部 ACTIVE）。 */
    List<PermissionCodeView> list(String appCode);
}
