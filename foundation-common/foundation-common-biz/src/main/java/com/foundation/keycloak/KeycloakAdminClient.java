package com.foundation.keycloak;

/**
 * Keycloak Admin REST API 的最小连接契约（F-02 BAS-01）。
 *
 * <p>本项只做连接与连通性校验；建 realm、内置应用、三员角色、应急管理员在 BAS-04、BAS-21 落地。
 */
public interface KeycloakAdminClient {

    /** 应用共用 realm，默认 {@code enterprise}（ADR-051 第 57 条）。 */
    String realm();

    /** 取 access_token 并读 realm 表示连通。 */
    boolean ping();

    /** {@code client_credentials} 取管理接口令牌，内部缓存至过期前。 */
    String accessToken();
}
