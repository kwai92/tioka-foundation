package com.foundation.account.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.account.api.dto.AccountStatus;
import com.foundation.account.api.dto.AccountType;
import com.foundation.account.domain.Account;
import com.foundation.account.infrastructure.mapper.AccountMapper;
import com.foundation.account.infrastructure.persistence.AccountDO;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 账号仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisAccountRepository implements AccountRepository {

    private final AccountMapper mapper;

    public MybatisAccountRepository(AccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Account> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisAccountRepository::toDomain);
    }

    @Override
    public Optional<Account> findByLoginName(String loginName) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<AccountDO>lambdaQuery().eq(AccountDO::getLoginName, loginName)))
                .map(MybatisAccountRepository::toDomain);
    }

    @Override
    public Optional<Account> findByPersonId(String personId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<AccountDO>lambdaQuery()
                .eq(AccountDO::getPersonId, personId)
                .eq(AccountDO::getType, AccountType.PERSONAL.name())))
                .map(MybatisAccountRepository::toDomain);
    }

    @Override
    public Optional<Account> findByKeycloakUserId(String keycloakUserId) {
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<AccountDO>lambdaQuery().eq(AccountDO::getKeycloakUserId, keycloakUserId)))
                .map(MybatisAccountRepository::toDomain);
    }

    @Override
    public List<Account> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectBatchIds(ids).stream().map(MybatisAccountRepository::toDomain).toList();
    }

    @Override
    public boolean existsByLoginName(String loginName) {
        if (loginName == null || loginName.isBlank()) {
            return false;
        }
        return mapper.selectCount(Wrappers.<AccountDO>lambdaQuery().eq(AccountDO::getLoginName, loginName)) > 0;
    }

    @Override
    public List<Account> findByType(AccountType type) {
        return mapper.selectList(Wrappers.<AccountDO>lambdaQuery()
                        .eq(AccountDO::getType, type.name())
                        .orderByAsc(AccountDO::getLoginName))
                .stream().map(MybatisAccountRepository::toDomain).toList();
    }

    @Override
    public List<Account> search(String q, AccountStatus status, AccountType type, int page, int size) {
        // 分页插件未装配，改用 SQL 级 LIMIT/OFFSET（00-施工总纲 §2 约定）
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, size);
        long offset = (long) (safePage - 1) * safeSize;
        return mapper.selectList(searchWrapper(q, status, type)
                        .last("LIMIT " + safeSize + " OFFSET " + offset))
                .stream().map(MybatisAccountRepository::toDomain).toList();
    }

    @Override
    public long count(String q, AccountStatus status, AccountType type) {
        return mapper.selectCount(searchWrapper(q, status, type));
    }

    @Override
    public List<Account> findAll() {
        return mapper.selectList(Wrappers.<AccountDO>lambdaQuery().orderByAsc(AccountDO::getLoginName))
                .stream().map(MybatisAccountRepository::toDomain).toList();
    }

    @Override
    public void insert(Account account) {
        mapper.insert(toDO(account));
    }

    @Override
    public void update(Account account) {
        mapper.update(null, Wrappers.<AccountDO>lambdaUpdate()
                .set(AccountDO::getLoginName, account.loginName())
                .set(AccountDO::getPersonId, account.personId())
                .set(AccountDO::getKeycloakUserId, account.keycloakUserId())
                .set(AccountDO::getStatus, account.status().name())
                .set(AccountDO::getType, account.type().name())
                .set(AccountDO::getLastLoginAt, toLocalDateTime(account.lastLoginAt()))
                .set(AccountDO::getVersion, account.version())
                .eq(AccountDO::getId, account.id()));
    }

    private LambdaQueryWrapper<AccountDO> searchWrapper(String q, AccountStatus status, AccountType type) {
        LambdaQueryWrapper<AccountDO> wrapper = Wrappers.<AccountDO>lambdaQuery();
        if (q != null && !q.isBlank()) {
            wrapper.apply("lower(login_name) LIKE lower({0})", "%" + q.trim() + "%");
        }
        if (status != null) {
            wrapper.eq(AccountDO::getStatus, status.name());
        }
        if (type != null) {
            wrapper.eq(AccountDO::getType, type.name());
        }
        wrapper.orderByAsc(AccountDO::getLoginName);
        return wrapper;
    }

    private static AccountDO toDO(Account account) {
        AccountDO row = new AccountDO();
        row.setId(account.id());
        row.setLoginName(account.loginName());
        row.setPersonId(account.personId());
        row.setKeycloakUserId(account.keycloakUserId());
        row.setStatus(account.status().name());
        row.setType(account.type().name());
        row.setLastLoginAt(toLocalDateTime(account.lastLoginAt()));
        row.setVersion(account.version());
        return row;
    }

    private static Account toDomain(AccountDO row) {
        return new Account(row.getId(), row.getLoginName(), row.getPersonId(), row.getKeycloakUserId(),
                AccountStatus.valueOf(row.getStatus()), AccountType.valueOf(row.getType()),
                row.getLastLoginAt() == null ? null : row.getLastLoginAt().toInstant(ZoneOffset.UTC),
                row.getVersion() == null ? 0 : row.getVersion());
    }

    private static LocalDateTime toLocalDateTime(java.time.Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
