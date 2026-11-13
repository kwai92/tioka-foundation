package com.foundation.authz.api;

import java.time.Instant;

/**
 * 清单导入结果（F-05 BAS-05 §清单导入规则）。
 *
 * @param appCode             应用编码
 * @param version             生效后的清单版本
 * @param contentHash         清单内容哈希（SHA-256）
 * @param idempotent          {@code true} 表示命中同哈希幂等返回，未产生新版本
 * @param menuCount           本次生效的菜单数
 * @param permissionCodeCount 本次生效的权限码数
 * @param offlineCount        本次被标下线的权限码/菜单数
 * @param importedAt          导入时间
 */
public record ManifestImportResult(String appCode,
                                   long version,
                                   String contentHash,
                                   boolean idempotent,
                                   int menuCount,
                                   int permissionCodeCount,
                                   int offlineCount,
                                   Instant importedAt) {
}
