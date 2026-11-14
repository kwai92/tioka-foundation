package com.foundation.account.infrastructure;

import com.foundation.account.api.dto.AccountStatus;
import com.foundation.account.api.dto.AccountType;
import com.foundation.account.domain.Account;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 账号仓储（F-04 §1、§5，MyBatis-Plus）。 */
public interface AccountRepository {

    Optional<Account> findById(String id);

    Optional<Account> findByLoginName(String loginName);

    /** 按人员取个人账号（一人最多一个，{@code type=PERSONAL}）。 */
    Optional<Account> findByPersonId(String personId);

    /** 按 Keycloak 用户 ID 取账号（对账回查、登录事件回填用）。 */
    Optional<Account> findByKeycloakUserId(String keycloakUserId);

    List<Account> findByIds(Collection<String> ids);

    /** 登录名是否已被占用（开通时生成与校验）。 */
    boolean existsByLoginName(String loginName);

    /** 按类型列出（服务账号列表用）。 */
    List<Account> findByType(AccountType type);

    /**
     * 检索账号列表。
     *
     * @param q    匹配登录名的模糊关键字，可空
     * @param page 页码（从 1 起）
     * @param size 每页行数
     */
    List<Account> search(String q, AccountStatus status, AccountType type, int page, int size);

    /** 检索总行数。 */
    long count(String q, AccountStatus status, AccountType type);

    /** 全量（每日对账扫描用）。 */
    List<Account> findAll();

    void insert(Account account);

    void update(Account account);
}
