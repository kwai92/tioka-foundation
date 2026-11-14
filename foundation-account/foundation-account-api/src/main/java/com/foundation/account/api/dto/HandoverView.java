package com.foundation.account.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * 交接待办视图（F-04 BAS-14 §1 第 3 步）：人员离职时，其作为负责人的服务账号进入本队列，
 * 由管理员指定新负责人。
 *
 * @param id                待办 ID（{@code hdo_…}）
 * @param ownerPersonId     原负责人（离职人员）
 * @param accountId         服务账号主体（BAS-03 {@code fdn_account.id}）
 * @param serviceAccountId  BAS-04 服务账号绑定 ID（{@code svc_…}）
 * @param appCode           所属应用编码
 * @param name              服务账号名称
 * @param permissionCodes   权限码白名单
 * @param status            待办状态：{@code PENDING} / {@code HANDED_OVER}
 * @param newOwnerPersonId  指定新负责人，未移交时为空
 * @param handledAt         移交时间，未移交时为空
 * @param createAt          进队列时间
 */
public record HandoverView(String id,
                           String ownerPersonId,
                           String accountId,
                           String serviceAccountId,
                           String appCode,
                           String name,
                           List<String> permissionCodes,
                           String status,
                           String newOwnerPersonId,
                           Instant handledAt,
                           Instant createAt) {
}
