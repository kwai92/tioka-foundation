package com.foundation.account.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.account.domain.HandoverStatus;
import com.foundation.account.domain.HandoverTodo;
import com.foundation.account.infrastructure.mapper.HandoverTodoMapper;
import com.foundation.account.infrastructure.persistence.HandoverTodoDO;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** 交接待办仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisHandoverTodoRepository implements HandoverTodoRepository {

    /** 权限码分隔符（权限码本身不含逗号）。 */
    private static final String CODE_SEPARATOR = ",";

    private final HandoverTodoMapper mapper;

    public MybatisHandoverTodoRepository(HandoverTodoMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(HandoverTodo todo) {
        HandoverTodoDO row = new HandoverTodoDO();
        row.setId(todo.id());
        row.setOwnerPersonId(todo.ownerPersonId());
        row.setAccountId(todo.accountId());
        row.setServiceAccountId(todo.serviceAccountId());
        row.setAppCode(todo.appCode());
        row.setName(todo.name());
        row.setPermissionCodes(join(todo.permissionCodes()));
        row.setStatus(todo.status().name());
        row.setNewOwnerPersonId(todo.newOwnerPersonId());
        row.setHandledAt(toLocalDateTime(todo.handledAt()));
        mapper.insert(row);
    }

    @Override
    public Optional<HandoverTodo> findPendingByAccountId(String accountId) {
        return mapper.selectList(Wrappers.<HandoverTodoDO>lambdaQuery()
                        .eq(HandoverTodoDO::getAccountId, accountId)
                        .eq(HandoverTodoDO::getStatus, HandoverStatus.PENDING.name()))
                .stream().findFirst().map(MybatisHandoverTodoRepository::toDomain);
    }

    @Override
    public List<HandoverTodo> findPending(String ownerPersonId) {
        return mapper.selectList(Wrappers.<HandoverTodoDO>lambdaQuery()
                        .eq(HandoverTodoDO::getStatus, HandoverStatus.PENDING.name())
                        .eq(ownerPersonId != null && !ownerPersonId.isBlank(),
                                HandoverTodoDO::getOwnerPersonId, ownerPersonId)
                        .orderByAsc(HandoverTodoDO::getCreateAt))
                .stream().map(MybatisHandoverTodoRepository::toDomain).toList();
    }

    @Override
    public void markHandedOver(String id, String newOwnerPersonId, Instant handledAt) {
        mapper.update(null, Wrappers.<HandoverTodoDO>lambdaUpdate()
                .set(HandoverTodoDO::getStatus, HandoverStatus.HANDED_OVER.name())
                .set(HandoverTodoDO::getNewOwnerPersonId, newOwnerPersonId)
                .set(HandoverTodoDO::getHandledAt, toLocalDateTime(handledAt))
                .eq(HandoverTodoDO::getId, id));
    }

    private static HandoverTodo toDomain(HandoverTodoDO row) {
        return new HandoverTodo(row.getId(), row.getOwnerPersonId(), row.getAccountId(),
                row.getServiceAccountId(), row.getAppCode(), row.getName(), split(row.getPermissionCodes()),
                HandoverStatus.valueOf(row.getStatus()), row.getNewOwnerPersonId(),
                row.getHandledAt() == null ? null : row.getHandledAt().toInstant(ZoneOffset.UTC),
                row.getCreateAt() == null ? null : row.getCreateAt().toInstant(ZoneOffset.UTC));
    }

    private static String join(List<String> codes) {
        return codes == null || codes.isEmpty() ? null : String.join(CODE_SEPARATOR, codes);
    }

    private static List<String> split(String codes) {
        return codes == null || codes.isBlank() ? List.of() : Arrays.asList(codes.split(CODE_SEPARATOR));
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
