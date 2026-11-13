package com.foundation.app.application;

import com.foundation.app.api.AppManifestVersionApi;
import com.foundation.app.domain.App;
import com.foundation.app.infrastructure.AppRepository;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/**
 * 应用权限清单版本读写（F-05 BAS-05 §清单导入规则）。
 *
 * <p>权限中心（{@code foundation-authz-biz}）经 {@link AppManifestVersionApi} 调用本实现，
 * 避免跨模块直连 {@code fdn_app}。
 */
public class AppManifestVersionService implements AppManifestVersionApi {

    private final AppRepository apps;

    public AppManifestVersionService(AppRepository apps) {
        this.apps = apps;
    }

    @Override
    public long currentVersion(String appCode) {
        return apps.find(appCode)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "应用不存在：" + appCode))
                .manifestVersion();
    }

    @Override
    public void advance(String appCode, long newVersion, String operator) {
        App app = apps.find(appCode)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "应用不存在：" + appCode));
        if (newVersion <= app.manifestVersion()) {
            throw new BizException(ErrorCode.CONFLICT,
                    "清单版本只能前进：当前 " + app.manifestVersion() + "，目标 " + newVersion);
        }
        apps.updateManifestVersion(app.id(), newVersion,
                operator == null || operator.isBlank() ? "system" : operator);
    }
}
