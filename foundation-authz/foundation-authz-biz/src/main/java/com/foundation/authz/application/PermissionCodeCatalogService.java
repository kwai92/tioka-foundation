package com.foundation.authz.application;

import com.foundation.authz.api.PermissionApi;
import com.foundation.authz.api.PermissionCodeView;
import com.foundation.authz.domain.PermissionCode;
import com.foundation.authz.infrastructure.PermissionCodeRepository;

import java.util.List;
import java.util.Optional;

/**
 * 权限码目录查询（F-05 BAS-05 §接口 {@code GET /api/permissions}）。
 *
 * <p>只返回在线权限码；{@code appCode} 非空时按应用过滤，避免全域权限码泄露。
 */
public class PermissionCodeCatalogService implements PermissionApi {

    private final PermissionCodeRepository permissions;
    private final AppIdResolver appIds;

    public PermissionCodeCatalogService(PermissionCodeRepository permissions, AppIdResolver appIds) {
        this.permissions = permissions;
        this.appIds = appIds;
    }

    @Override
    public List<PermissionCodeView> list(String appCode) {
        Optional<String> appId = appIds.resolve(appCode);
        List<PermissionCode> found = appId.isPresent()
                ? permissions.listByApp(appId.get())
                : permissions.listActive();
        return found.stream().filter(PermissionCode::active).map(PermissionCodeCatalogService::toView).toList();
    }

    private static PermissionCodeView toView(PermissionCode permissionCode) {
        return new PermissionCodeView(permissionCode.id(), permissionCode.appId(), permissionCode.code(),
                permissionCode.name(), permissionCode.riskLevel().name(), permissionCode.status(),
                permissionCode.firstSeenAt(), permissionCode.offlineAt());
    }
}
