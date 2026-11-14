package com.foundation.account.infrastructure;

import com.foundation.account.domain.HandoverTodo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 交接待办仓储（F-04 BAS-14 §1 第 3 步）。 */
public interface HandoverTodoRepository {

    /** 入队。 */
    void insert(HandoverTodo todo);

    /** 按服务账号主体取待移交待办（同一账号只保留一条 {@code PENDING}）。 */
    Optional<HandoverTodo> findPendingByAccountId(String accountId);

    /** 待移交队列；{@code ownerPersonId} 为空表示全部人员。 */
    List<HandoverTodo> findPending(String ownerPersonId);

    /** 标记已移交给新负责人。 */
    void markHandedOver(String id, String newOwnerPersonId, Instant handledAt);
}
