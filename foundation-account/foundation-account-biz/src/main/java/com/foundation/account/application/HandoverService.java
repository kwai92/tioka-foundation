package com.foundation.account.application;

import com.foundation.account.api.dto.HandoverView;
import com.foundation.account.config.OffboardingProperties;
import com.foundation.account.domain.AccountIdPrefix;
import com.foundation.account.domain.HandoverStatus;
import com.foundation.account.domain.HandoverTodo;
import com.foundation.account.infrastructure.HandoverTodoRepository;
import com.foundation.app.api.AppApi;
import com.foundation.app.api.AppView;
import com.foundation.app.api.ServiceAccountApi;
import com.foundation.app.api.ServiceAccountView;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import com.foundation.org.api.PersonApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 交接待办（F-04 BAS-14 §1 第 3 步、§4）：人员离职时把其作为 {@code owner_user_id} 的服务账号
 * 装入待办队列；管理员指定新负责人后移交（复用 BAS-04 的 {@link ServiceAccountApi#transferOwner}）。
 *
 * <p>「在门户登记为处理人的待办」属消息中心（BAS-16）职责，本服务只生成服务账号交接待办。
 */
public class HandoverService {

    private static final Logger log = LoggerFactory.getLogger(HandoverService.class);

    private final HandoverTodoRepository todos;
    private final AppApi apps;
    private final ServiceAccountApi serviceAccounts;
    private final PersonApi persons;
    private final OffboardingProperties properties;

    public HandoverService(HandoverTodoRepository todos, AppApi apps, ServiceAccountApi serviceAccounts,
                           PersonApi persons, OffboardingProperties properties) {
        this.todos = todos;
        this.apps = apps;
        this.serviceAccounts = serviceAccounts;
        this.persons = persons;
        this.properties = properties;
    }

    /** 待移交队列；{@code ownerPersonId} 为空表示全部人员（F-04 §4 {@code GET /api/service-accounts/handover}）。 */
    public List<HandoverView> list(String ownerPersonId) {
        return todos.findPending(ownerPersonId).stream().map(HandoverTodo::toView).toList();
    }

    /**
     * 把该人员负责的服务账号装入交接待办队列（幂等：同一服务账号只保留一条待移交待办）。
     *
     * @return 本次新增条数
     */
    @Transactional
    public int enqueue(String ownerPersonId) {
        if (ownerPersonId == null || ownerPersonId.isBlank() || !properties.isCreateHandoverTodo()) {
            return 0;
        }
        int created = 0;
        for (AppView app : apps.list()) {
            for (ServiceAccountView serviceAccount : serviceAccounts.listByApp(app.code())) {
                if (!ownerPersonId.equals(serviceAccount.ownerUserId())
                        || todos.findPendingByAccountId(serviceAccount.accountId()).isPresent()) {
                    continue;
                }
                todos.insert(new HandoverTodo(Ids.newId(AccountIdPrefix.HANDOVER_TODO), ownerPersonId,
                        serviceAccount.accountId(), serviceAccount.id(), app.code(), serviceAccount.name(),
                        serviceAccount.permissionCodes(), HandoverStatus.PENDING, null, null, Instant.now()));
                created++;
            }
        }
        log.info("交接待办入队：person={} 新增={}", ownerPersonId, created);
        return created;
    }

    /** 指定新负责人（F-04 §4 {@code POST /api/service-accounts/{id}/handover}，{@code id} 为服务账号主体）。 */
    @Transactional
    public HandoverView handover(String accountId, String newOwnerPersonId, String operator) {
        if (newOwnerPersonId == null || newOwnerPersonId.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "新负责人必填");
        }
        persons.findById(newOwnerPersonId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "新负责人不存在：" + newOwnerPersonId));
        HandoverTodo todo = todos.findPendingByAccountId(accountId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "交接待办不存在或已移交：" + accountId));
        serviceAccounts.transferOwner(todo.serviceAccountId(), newOwnerPersonId, operator);
        Instant handledAt = Instant.now();
        todos.markHandedOver(todo.id(), newOwnerPersonId, handledAt);
        return todo.handedOver(newOwnerPersonId, handledAt).toView();
    }
}
