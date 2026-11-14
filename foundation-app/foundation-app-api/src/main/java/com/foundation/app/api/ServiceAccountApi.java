package com.foundation.app.api;

import java.time.Instant;
import java.util.List;

/**
 * 服务账号契约（F-05 BAS-04 §接口：{@code GET|POST /api/apps/{code}/service-accounts}、{@code DELETE /{id}}）。
 *
 * <p>账号主体是 BAS-03 的 {@code fdn_account}（{@code type=SERVICE}）；本项只存「账号与应用的绑定 + 可用权限码」。
 */
public interface ServiceAccountApi {

    /** 某应用下的服务账号列表（按名称升序）。 */
    List<ServiceAccountView> listByApp(String appCode);

    /** 登记服务账号；同时开通 {@code client_credentials} 客户端。
     *
     * @return 服务账号 ID（{@code svc_{ulid}}）
     */
    String create(ServiceAccountCmd cmd);

    /** 停用服务账号（{@code status=DISABLED}），其客户端同时停用。 */
    void disable(String serviceAccountId, String operator);

    /** 移交给新负责人（F-04 BAS-14 交接待办：{@code POST /api/service-accounts/{id}/handover}）。 */
    void transferOwner(String serviceAccountId, String newOwnerPersonId, String operator);

    /** 过期时间已到且仍为 {@code ACTIVE} 的服务账号拉平为 {@code DISABLED}，返回处理条数。 */
    int disableExpired(Instant now);
}
