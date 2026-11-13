package com.foundation.authz.application;

import com.foundation.app.api.AppApi;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.util.Optional;

/**
 * 应用编码 → 应用 ID 解析（F-05 BAS-05 §领域模型）。
 *
 * <p>菜单与角色按 {@code app_id} 归属，入参却是应用编码；跨模块经 {@link AppApi} 契约取，
 * 不直连 {@code fdn_app}（48-CS §9：跨模块只依赖对方 {@code -api}）。
 */
public class AppIdResolver {

    private final AppApi apps;

    public AppIdResolver(AppApi apps) {
        this.apps = apps;
    }

    /**
     * 解析应用 ID。
     *
     * @param appCode 应用编码；为空表示不按应用过滤（返回空）
     * @throws BizException 编码非空但应用不存在（{@code NOT_FOUND}）
     */
    public Optional<String> resolve(String appCode) {
        if (appCode == null || appCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(apps.find(appCode)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "应用不存在：" + appCode))
                .id());
    }
}
