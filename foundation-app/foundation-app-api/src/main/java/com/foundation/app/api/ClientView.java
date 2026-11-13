package com.foundation.app.api;

import java.time.Instant;
import java.util.List;

/**
 * 应用客户端视图（F-05 BAS-04 §领域模型）。
 *
 * @param id              客户端 ID（{@code cli_{ulid}}）
 * @param appId           归属应用
 * @param clientId        Keycloak {@code clientId}
 * @param redirectUris    回调地址（逗号分隔存储，读时拆分为列表）
 * @param grantTypes      授权类型（逗号分隔存储，读时拆分为列表）
 * @param status          {@code ACTIVE} / {@code ROTATING} / {@code DISABLED}
 * @param secretRotatedAt 最近一次密钥轮换时间，可空
 */
public record ClientView(String id,
                         String appId,
                         String clientId,
                         List<String> redirectUris,
                         List<String> grantTypes,
                         String status,
                         Instant secretRotatedAt) {
}
