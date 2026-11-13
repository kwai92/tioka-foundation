package com.foundation.app.api;

/**
 * 应用变更命令（F-05 BAS-04 §接口 {@code PUT /api/apps/{code}}）。
 *
 * <p>不含 {@code code}：应用编码不可改，按路径上的编码定位。
 *
 * @param code        定位用编码
 * @param name        名称
 * @param icon        图标，可空
 * @param accessUrl   访问地址，可空
 * @param ownerUserId 负责人
 * @param status      {@code ENABLED} / {@code DISABLED}
 * @param operator    操作人
 */
public record AppUpdateCmd(String code,
                           String name,
                           String icon,
                           String accessUrl,
                           String ownerUserId,
                           String status,
                           String operator) {
}
