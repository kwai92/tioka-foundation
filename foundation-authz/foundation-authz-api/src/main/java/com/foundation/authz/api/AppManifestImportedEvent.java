package com.foundation.authz.api;

import java.time.Instant;

/**
 * 权限清单导入事件（F-05 BAS-05 §事件 {@code fdn.app.manifest_imported}）。
 *
 * <p>经 outbox → RocketMQ 主题 {@code fdn-events} 投递；订阅方为管理台（刷新目录树）与审计。
 */
public record AppManifestImportedEvent(String appCode,
                                       long version,
                                       String contentHash,
                                       int menuCount,
                                       int permissionCodeCount,
                                       int offlineCount,
                                       String importedBy,
                                       Instant importedAt) {

    /** outbox 事件名（F-05 BAS-05 §事件）。 */
    public static final String EVENT_TYPE = "fdn.app.manifest_imported";
}
