package com.foundation.common.api.keycloak;

import java.time.Instant;
import java.util.List;

/**
 * Keycloak 登录事件读取契约（F-09 BAS-10 §登录日志）。
 *
 * <p>跨模块只依赖 {@code -api}：审计领域只依赖本接口，实现落在 {@code foundation-common-biz} 的 Keycloak 客户端内；
 * 缺失时登录日志采集降级（不采集、不报错）。
 */
public interface KeycloakLoginEventAdmin {

    /** 拉取 {@code since} 之后的 LOGIN / LOGIN_ERROR 事件（实现按 first/max 翻页，最多 max 条）。 */
    List<KeycloakLoginEvent> listLoginEvents(Instant since, int max);
}
