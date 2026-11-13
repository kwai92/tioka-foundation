package com.foundation.app.api;

import java.util.List;
import java.util.Optional;

/**
 * 应用登记契约（F-05 BAS-04 §接口）。
 *
 * <p>跨模块只依赖本 {@code -api}；{@code code} 登记后不可改（§领域模型）。
 */
public interface AppApi {

    /** 应用列表（按编码升序）。 */
    List<AppView> list();

    /** 按应用编码查询；不存在返回空。 */
    Optional<AppView> find(String code);

    /**
     * 登记应用并自动开通 OIDC 客户端（Keycloak 调用失败时本地不落半成品）。
     *
     * @return 新建应用 ID（{@code app_{ulid}}）
     */
    String register(AppRegistrationCmd cmd);

    /** 改名称/图标/地址/负责人/状态；{@code code} 不可改。 */
    void update(AppUpdateCmd cmd);
}
