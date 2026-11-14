package com.foundation.account.application;

import com.foundation.account.api.dto.AccountStatus;
import com.foundation.account.api.dto.AccountType;
import com.foundation.account.api.dto.AccountView;
import com.foundation.account.api.dto.ServiceAccountCreateCmd;
import com.foundation.account.domain.Account;
import com.foundation.account.domain.AccountIdPrefix;
import com.foundation.account.domain.AccountRules;
import com.foundation.account.domain.PushAction;
import com.foundation.account.infrastructure.AccountRepository;
import com.foundation.app.api.AppApi;
import com.foundation.app.api.AppView;
import com.foundation.app.api.ServiceAccountApi;
import com.foundation.app.api.ServiceAccountCmd;
import com.foundation.app.api.ServiceAccountView;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import com.foundation.org.api.PersonApi;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 服务账号应用服务（F-04 §3、§7）。
 *
 * <p>账号主体落在本模块 {@code fdn_account}（{@code type=SERVICE}，不关联人员）；应用绑定与权限码白名单
 * 由 BAS-04 的 {@link ServiceAccountApi} 维护（复用其 {@code fdn_service_account}，本项不改其实现）；
 * 客户端（{@code client_credentials}）与密钥轮换归 BAS-04。
 */
public class ServiceAccountApplicationService {

    /** 生成服务账号登录名时的冲突后缀上限。 */
    private static final int MAX_SUFFIX = 1000;

    private final AccountRepository accounts;
    private final PersonApi persons;
    private final AppApi apps;
    private final ServiceAccountApi serviceAccounts;
    private final AccountPushService pushService;

    public ServiceAccountApplicationService(AccountRepository accounts, PersonApi persons, AppApi apps,
                                            ServiceAccountApi serviceAccounts, AccountPushService pushService) {
        this.accounts = accounts;
        this.persons = persons;
        this.apps = apps;
        this.serviceAccounts = serviceAccounts;
        this.pushService = pushService;
    }

    /**
     * 新建服务账号（F-04 §7 {@code POST /api/service-accounts}）：先建 {@code fdn_account}（{@code type=SERVICE}）
     * 并推送 Keycloak，再由 BAS-04 登记绑定并开通 {@code client_credentials} 客户端。
     *
     * @return 账号 ID（{@code acc_…}）
     */
    @Transactional
    public String create(ServiceAccountCreateCmd cmd) {
        if (cmd == null || cmd.appCode() == null || cmd.appCode().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "应用编码必填");
        }
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "服务账号名称必填");
        }
        if (cmd.ownerPersonId() == null || cmd.ownerPersonId().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "负责人必填");
        }
        apps.find(cmd.appCode())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "应用不存在：" + cmd.appCode()));
        persons.findById(cmd.ownerPersonId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "负责人不存在：" + cmd.ownerPersonId()));

        String loginName = uniqueLoginName(cmd.appCode(), cmd.name());
        Account account = new Account(Ids.newId(AccountIdPrefix.ACCOUNT), loginName, null, null,
                AccountRules.initialStatus(AccountType.SERVICE), AccountType.SERVICE, null, 0L);
        accounts.insert(account);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("firstName", cmd.name());
        payload.put("enabled", true);
        // 服务账号只走 client_credentials，不设首次改密等交互式登录动作（F-04 §6 对账期望为空）
        payload.put("requiredActions", List.of());
        pushService.push(account.id(), PushAction.CREATE_USER, payload);

        serviceAccounts.create(new ServiceAccountCmd(cmd.appCode(), account.id(), cmd.name(),
                cmd.permissionCodes(), cmd.ownerPersonId(), cmd.expiresAt(), cmd.operator()));
        return account.id();
    }

    /** 按账号 ID 取服务账号视图（新建后回读）。 */
    public Optional<AccountView> findById(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return Optional.empty();
        }
        return accounts.findById(accountId)
                .filter(account -> account.type() == AccountType.SERVICE)
                .map(Account::toView);
    }

    /** 服务账号列表；{@code appCode} 为空表示全部（F-04 §7 {@code GET /api/service-accounts}）。 */
    public List<AccountView> list(String appCode) {
        if (appCode == null || appCode.isBlank()) {
            return accounts.findByType(AccountType.SERVICE).stream().map(Account::toView).toList();
        }
        List<String> accountIds = serviceAccounts.listByApp(appCode).stream()
                .map(ServiceAccountView::accountId).toList();
        return byAccountIds(accountIds);
    }

    /** 按负责人列出服务账号（BAS-14 交接待办用）。 */
    public List<AccountView> listByOwner(String ownerPersonId) {
        if (ownerPersonId == null || ownerPersonId.isBlank()) {
            return List.of();
        }
        Set<String> accountIds = new LinkedHashSet<>();
        for (AppView app : apps.list()) {
            for (ServiceAccountView serviceAccount : serviceAccounts.listByApp(app.code())) {
                if (ownerPersonId.equals(serviceAccount.ownerUserId())) {
                    accountIds.add(serviceAccount.accountId());
                }
            }
        }
        return byAccountIds(accountIds);
    }

    /** 停用服务账号：账号置 {@code DISABLED} 并停用 BAS-04 绑定（F-04 §7）。 */
    @Transactional
    public void disable(String accountId, String operator) {
        Account account = requireServiceAccount(accountId);
        if (AccountRules.canDisable(account.status())) {
            accounts.update(account.withStatus(AccountStatus.DISABLED));
            pushService.push(accountId, PushAction.SET_ENABLED, Map.of("enabled", false));
        }
        findServiceAccountId(accountId).ifPresent(id -> serviceAccounts.disable(id, operator));
    }

    /** 启用服务账号（F-04 §7）。 */
    @Transactional
    public void enable(String accountId, String operator) {
        Account account = requireServiceAccount(accountId);
        if (account.status() != AccountStatus.NORMAL) {
            accounts.update(account.withStatus(AccountStatus.NORMAL));
            pushService.push(accountId, PushAction.SET_ENABLED, Map.of("enabled", true));
        }
    }

    private List<AccountView> byAccountIds(java.util.Collection<String> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return List.of();
        }
        return accounts.findByIds(accountIds).stream().map(Account::toView).toList();
    }

    /** 在 BAS-04 的绑定里按账号 ID 找服务账号 ID（无按账号查询的契约，故按应用遍历）。 */
    private Optional<String> findServiceAccountId(String accountId) {
        for (AppView app : apps.list()) {
            for (ServiceAccountView serviceAccount : serviceAccounts.listByApp(app.code())) {
                if (accountId.equals(serviceAccount.accountId())) {
                    return Optional.of(serviceAccount.id());
                }
            }
        }
        return Optional.empty();
    }

    private Account requireServiceAccount(String accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "账号不存在：" + accountId));
        if (account.type() != AccountType.SERVICE) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非服务账号：" + accountId);
        }
        return account;
    }

    /** 生成服务账号登录名：{@code appCode.name} 小写，非法字符替换为 {@code -}，冲突时追加后缀。 */
    private String uniqueLoginName(String appCode, String name) {
        String base = (appCode + "." + name).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._@-]", "-");
        if (!accounts.existsByLoginName(base)) {
            return base;
        }
        for (int suffix = 2; suffix <= MAX_SUFFIX; suffix++) {
            String candidate = base + "-" + suffix;
            if (!accounts.existsByLoginName(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("服务账号登录名冲突过多，无法生成：" + base);
    }
}
