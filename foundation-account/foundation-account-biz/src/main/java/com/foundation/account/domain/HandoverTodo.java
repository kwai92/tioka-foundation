package com.foundation.account.domain;

import com.foundation.account.api.dto.HandoverView;

import java.time.Instant;
import java.util.List;

/**
 * 交接待办（F-04 BAS-14 §1 第 3 步，{@code fdn_handover_todo}）。
 *
 * <p>人员离职时，其作为 {@code owner_user_id} 的服务账号进入本队列；管理员指定新负责人后置
 * {@link HandoverStatus#HANDED_OVER}。同一服务账号只保留一条待移交待办。
 *
 * @param id                待办 ID（{@code hdo_…}）
 * @param ownerPersonId     原负责人（离职人员）
 * @param accountId         服务账号主体（BAS-03 {@code fdn_account.id}）
 * @param serviceAccountId  BAS-04 服务账号绑定 ID（{@code svc_…}）
 * @param appCode           所属应用编码
 * @param name              服务账号名称
 * @param permissionCodes   权限码白名单
 * @param status            待办状态
 * @param newOwnerPersonId  指定新负责人，未移交时为空
 * @param handledAt         移交时间，未移交时为空
 * @param createAt          进队列时间
 */
public record HandoverTodo(String id,
                           String ownerPersonId,
                           String accountId,
                           String serviceAccountId,
                           String appCode,
                           String name,
                           List<String> permissionCodes,
                           HandoverStatus status,
                           String newOwnerPersonId,
                           Instant handledAt,
                           Instant createAt) {

    /** 移交后的副本。 */
    public HandoverTodo handedOver(String newOwner, Instant at) {
        return new HandoverTodo(id, ownerPersonId, accountId, serviceAccountId, appCode, name,
                permissionCodes, HandoverStatus.HANDED_OVER, newOwner, at, createAt);
    }

    /** 转对外视图。 */
    public HandoverView toView() {
        return new HandoverView(id, ownerPersonId, accountId, serviceAccountId, appCode, name,
                permissionCodes == null ? List.of() : permissionCodes,
                status.name(), newOwnerPersonId, handledAt, createAt);
    }
}
