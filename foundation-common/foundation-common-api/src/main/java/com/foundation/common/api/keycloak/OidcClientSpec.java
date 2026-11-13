package com.foundation.common.api.keycloak;

import java.util.List;

/**
 * OIDC 客户端登记期望（F-05 BAS-04）：应用登记时按此在 Keycloak 建或改客户端。
 *
 * @param clientId      Keycloak {@code clientId}（realm 内唯一）
 * @param name          客户端显示名
 * @param redirectUris  回调地址（精确匹配；{@code authorization_code} 流程必填）
 * @param grantTypes    授权类型集合，取值见 {@link OidcGrantTypes}
 */
public record OidcClientSpec(String clientId, String name, List<String> redirectUris, List<String> grantTypes) {
}
