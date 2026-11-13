package com.foundation.app.api;

import java.util.List;

/**
 * 应用登记命令（F-05 BAS-04 §接口 {@code POST /api/apps}）。
 *
 * @param code          应用编码，全局唯一且不可改，正则 {@code ^[a-z][a-z0-9-]{1,31}$}
 * @param name          名称
 * @param icon          图标（URL 或图标名），可空
 * @param accessUrl     访问地址（门户入口与单点登录回跳默认地址），可空
 * @param ownerUserId   负责人（人员 ID）
 * @param kind          {@code PORTAL} / {@code ADMIN} / {@code SUB}
 * @param redirectUris  客户端回调地址（{@code authorization_code} 流程必填）
 * @param operator      操作人（审计列 create_by/update_by）
 */
public record AppRegistrationCmd(String code,
                                 String name,
                                 String icon,
                                 String accessUrl,
                                 String ownerUserId,
                                 String kind,
                                 List<String> redirectUris,
                                 String operator) {
}
