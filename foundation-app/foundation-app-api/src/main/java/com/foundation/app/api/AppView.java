package com.foundation.app.api;

/**
 * 应用视图（F-05 BAS-04 §领域模型）。
 *
 * @param id              应用 ID（{@code app_{ulid}}）
 * @param code            应用编码（不可改）
 * @param name            名称
 * @param icon            图标，可空
 * @param accessUrl       访问地址，可空
 * @param kind            {@code PORTAL} / {@code ADMIN} / {@code SUB}
 * @param ownerUserId     负责人
 * @param status          {@code ENABLED} / {@code DISABLED}
 * @param manifestVersion 当前生效的权限清单版本号（BAS-05 清单导入时递增）
 */
public record AppView(String id,
                      String code,
                      String name,
                      String icon,
                      String accessUrl,
                      String kind,
                      String ownerUserId,
                      String status,
                      long manifestVersion) {
}
