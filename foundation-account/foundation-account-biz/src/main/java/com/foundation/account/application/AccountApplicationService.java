package com.foundation.account.application;

import com.foundation.account.api.dto.AccountCreateCmd;
import com.foundation.account.api.dto.AccountReconcileView;
import com.foundation.account.api.dto.AccountResetResult;
import com.foundation.account.api.dto.AccountSessionView;
import com.foundation.account.api.dto.AccountStatus;
import com.foundation.account.api.dto.AccountType;
import com.foundation.account.api.dto.AccountView;
import com.foundation.account.api.dto.EmergencyEnableTicket;
import com.foundation.account.api.dto.PageResult;
import com.foundation.account.domain.Account;
import com.foundation.account.domain.AccountIdPrefix;
import com.foundation.account.domain.AccountReconcileRecord;
import com.foundation.account.domain.AccountRules;
import com.foundation.account.domain.LoginNameRule;
import com.foundation.account.domain.PushAction;
import com.foundation.account.config.AccountProperties;
import com.foundation.account.infrastructure.AccountEventOutbox;
import com.foundation.account.infrastructure.AccountReconcileRepository;
import com.foundation.account.infrastructure.AccountRepository;
import com.foundation.account.infrastructure.EmergencyEnableStore;
import com.foundation.account.infrastructure.EmergencyEnableStore.EmergencyEnableRequest;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.api.keycloak.KeycloakSessionView;
import com.foundation.common.api.keycloak.KeycloakUserAdmin;
import com.foundation.common.id.Ids;
import com.foundation.org.api.PersonApi;
import com.foundation.org.api.dto.PersonView;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 账号应用服务（F-04 §1、§5、§7）：开通、停用、启用、锁定、解锁、重置与在线会话的**唯一事务边界**。
 *
 * <p>所有写操作先落 Foundation 库、再推送 Keycloak（{@link AccountPushService}）；Foundation 不保存口令。
 * 停用连带吊销会话并发 {@code fdn.account.sessions_revoked}（F-04 §7、BAS-14 §2.4）。
 */
public class AccountApplicationService {

    /** 临时口令字符集（满足密码策略：大小写、数字、符号）。 */
    private static final String TEMP_PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%^&*";

    private static final int TEMP_PASSWORD_LENGTH = 14;

    private final AccountRepository accounts;
    private final PersonApi persons;
    private final KeycloakUserAdmin keycloak;
    private final AccountPushService pushService;
    private final AccountEventOutbox outbox;
    private final EmergencyEnableStore emergencyStore;
    private final AccountReconcileRepository reconciles;
    private final AccountProperties properties;
    private final SecureRandom random = new SecureRandom();

    public AccountApplicationService(AccountRepository accounts, PersonApi persons, KeycloakUserAdmin keycloak,
                                     AccountPushService pushService, AccountEventOutbox outbox,
                                     EmergencyEnableStore emergencyStore, AccountReconcileRepository reconciles,
                                     AccountProperties properties) {
        this.accounts = accounts;
        this.persons = persons;
        this.keycloak = keycloak;
        this.pushService = pushService;
        this.outbox = outbox;
        this.emergencyStore = emergencyStore;
        this.reconciles = reconciles;
        this.properties = properties;
    }

    /** 按账号 ID 取账号。 */
    public Optional<AccountView> findById(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return Optional.empty();
        }
        return accounts.findById(accountId).map(Account::toView);
    }

    /** 按人员取个人账号（一人最多一个）。 */
    public Optional<AccountView> findByPersonId(String personId) {
        if (personId == null || personId.isBlank()) {
            return Optional.empty();
        }
        return accounts.findByPersonId(personId).map(Account::toView);
    }

    /** 按登录名取账号。 */
    public Optional<AccountView> findByLoginName(String loginName) {
        if (loginName == null || loginName.isBlank()) {
            return Optional.empty();
        }
        return accounts.findByLoginName(loginName).map(Account::toView);
    }

    /** 检索账号列表（F-04 §7 {@code GET /api/accounts}）。 */
    public PageResult<AccountView> search(String q, AccountStatus status, AccountType type, int page, int size) {
        List<AccountView> items = accounts.search(q, status, type, page, size).stream()
                .map(Account::toView).toList();
        return PageResult.of(items, accounts.count(q, status, type), page, size);
    }

    /** 最近的每日对账记录（F-04 §6）。 */
    public List<AccountReconcileView> recentReconciles(int limit) {
        return reconciles.recent(limit <= 0 ? 20 : limit).stream()
                .map(AccountReconcileRecord::toView).toList();
    }

    /** 开通个人账号并推送 Keycloak，返回账号 ID（F-04 §7 {@code POST /api/accounts}）。 */
    @Transactional
    public String createPersonal(AccountCreateCmd cmd) {
        if (cmd.personId() == null || cmd.personId().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "人员 ID 必填");
        }
        PersonView person = persons.findById(cmd.personId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "人员不存在：" + cmd.personId()));
        AccountRules.requirePersonBinding(AccountType.PERSONAL, person.id());
        AccountRules.requireNoExistingPersonal(accounts.findByPersonId(person.id()).isPresent());

        String loginName = resolveLoginName(cmd.loginName(), person.empNo());
        Account account = new Account(Ids.newId(AccountIdPrefix.ACCOUNT), loginName, person.id(), null,
                AccountRules.initialStatus(AccountType.PERSONAL), AccountType.PERSONAL, null, 0L);
        accounts.insert(account);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", person.email());
        payload.put("firstName", person.name());
        payload.put("enabled", true);
        payload.put("requiredActions", properties.getProvision().getRequiredActions());
        pushService.push(account.id(), PushAction.CREATE_USER, payload);
        return account.id();
    }

    /** 停用账号（幂等）：状态置 {@code DISABLED}、推送 {@code enabled=false}，并连带吊销会话（F-04 §5、BAS-14）。 */
    @Transactional
    public void disable(String accountId, String operator) {
        Account account = requireAccount(accountId);
        if (!AccountRules.canDisable(account.status())) {
            return;
        }
        Account disabled = account.withStatus(AccountStatus.DISABLED);
        accounts.update(disabled);
        pushService.push(accountId, PushAction.SET_ENABLED, Map.of("enabled", false));
        revokeSessionsInternal(disabled);
    }

    /**
     * 停用账号但不吊销会话（离职链 {@code foundation.offboarding.revoke-on-disable=false} 时使用）。
     *
     * <p>与 {@link #disable(String, String)} 的差别仅在于不吊销会话、不发 {@code fdn.account.sessions_revoked}。
     */
    @Transactional
    public void disableWithoutRevokingSessions(String accountId, String operator) {
        Account account = requireAccount(accountId);
        if (!AccountRules.canDisable(account.status())) {
            return;
        }
        accounts.update(account.withStatus(AccountStatus.DISABLED));
        pushService.push(accountId, PushAction.SET_ENABLED, Map.of("enabled", false));
    }

    /** 重新开通账号（F-04 §7 {@code PUT /api/accounts/{id}/enable}）。 */
    @Transactional
    public void enable(String accountId, String operator) {
        Account account = requireAccount(accountId);
        if (account.status() == AccountStatus.NORMAL) {
            return;
        }
        Account enabled = account.withStatus(AccountStatus.NORMAL);
        accounts.update(enabled);
        pushService.push(accountId, PushAction.SET_ENABLED, Map.of("enabled", true));
    }

    /** 锁定账号（可解除；F-04 §1）。 */
    @Transactional
    public void lock(String accountId, String operator) {
        Account account = requireAccount(accountId);
        if (!AccountRules.canLock(account.status())) {
            return;
        }
        Account locked = account.withStatus(AccountStatus.LOCKED);
        accounts.update(locked);
        pushService.push(accountId, PushAction.SET_ENABLED, Map.of("enabled", false));
    }

    /** 解锁账号（F-04 §1）。 */
    @Transactional
    public void unlock(String accountId, String operator) {
        Account account = requireAccount(accountId);
        if (!AccountRules.canUnlock(account.status())) {
            return;
        }
        Account unlocked = account.withStatus(AccountStatus.NORMAL);
        accounts.update(unlocked);
        pushService.push(accountId, PushAction.SET_ENABLED, Map.of("enabled", true));
    }

    /**
     * 重置口令（F-04 §5）：按配置走邮件重置或一次性临时口令。
     *
     * <p>临时口令只在本结果的 {@code temporaryPassword} 中出现一次，**不落库、不进日志**。
     */
    @Transactional
    public AccountResetResult resetPassword(String accountId, String operator) {
        Account account = requireAccount(accountId);
        if (account.keycloakUserId() == null || account.keycloakUserId().isBlank()) {
            throw new BizException(ErrorCode.CONFLICT, "账号尚未推送 Keycloak，无法重置口令：" + accountId);
        }
        if ("temporary-password".equalsIgnoreCase(properties.getProvision().getResetVia())) {
            String temporaryPassword = generateTemporaryPassword();
            keycloak.resetPassword(account.keycloakUserId(), temporaryPassword, true);
            return new AccountResetResult(false, temporaryPassword);
        }
        boolean sent = pushService.push(accountId, PushAction.RESET_PASSWORD, Map.of());
        return new AccountResetResult(sent, null);
    }

    /** 账号在线会话（来自 Keycloak，F-04 §7）。 */
    public List<AccountSessionView> sessions(String accountId) {
        Account account = requireAccount(accountId);
        if (account.keycloakUserId() == null || account.keycloakUserId().isBlank()) {
            return List.of();
        }
        return keycloak.listSessions(account.keycloakUserId()).stream()
                .map(AccountApplicationService::toSessionView)
                .toList();
    }

    /** 强制下线并吊销全部会话，发 {@code fdn.account.sessions_revoked}（F-04 §7）。 */
    @Transactional
    public void revokeSessions(String accountId, String operator) {
        revokeSessionsInternal(requireAccount(accountId));
    }

    /** 应急管理员启用第一步（发起）：返回一次性凭据（F-04 §7 双人确认）。 */
    @Transactional
    public EmergencyEnableTicket requestEmergencyEnable(String accountId, String operator) {
        if (!properties.getEmergencyAdmin().isEnabled()) {
            throw new BizException(ErrorCode.FORBIDDEN, "应急管理员未启用");
        }
        Account account = requireAccount(accountId);
        if (account.type() != AccountType.EMERGENCY_ADMIN) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非应急管理员账号：" + accountId);
        }
        if (account.status() == AccountStatus.NORMAL) {
            throw new BizException(ErrorCode.CONFLICT, "应急管理员已启用：" + accountId);
        }
        String requestId = Ids.newId(AccountIdPrefix.EMERGENCY_REQUEST);
        Duration ttl = Duration.ofSeconds(properties.getEmergencyAdmin().getRequestTtlSeconds());
        emergencyStore.save(requestId, accountId, operator, ttl);
        return new EmergencyEnableTicket(requestId, accountId, Instant.now().plus(ttl));
    }

    /** 应急管理员启用第二步（另一名系统管理员确认，四眼；F-04 §7）。 */
    @Transactional
    public void confirmEmergencyEnable(String accountId, String requestId, String operator) {
        if (!properties.getEmergencyAdmin().isEnabled()) {
            throw new BizException(ErrorCode.FORBIDDEN, "应急管理员未启用");
        }
        EmergencyEnableRequest request = emergencyStore.take(requestId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "启用申请不存在或已失效"));
        if (!request.accountId().equals(accountId)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "启用申请与账号不匹配");
        }
        if (properties.getEmergencyAdmin().isRequireTwoPerson()
                && operator != null && operator.equals(request.requester())) {
            throw new BizException(ErrorCode.FORBIDDEN, "应急管理员启用需另一名系统管理员确认");
        }
        Account account = requireAccount(accountId);
        if (account.status() == AccountStatus.NORMAL) {
            return;
        }
        Account enabled = account.withStatus(AccountStatus.NORMAL);
        accounts.update(enabled);
        pushService.push(accountId, PushAction.SET_ENABLED, Map.of("enabled", true));
    }

    private void revokeSessionsInternal(Account account) {
        if (account.keycloakUserId() != null && !account.keycloakUserId().isBlank()) {
            pushService.push(account.id(), PushAction.REVOKE_SESSIONS, Map.of());
        }
        outbox.sessionsRevoked(account.id(), account.version());
    }

    private String resolveLoginName(String requested, String empNo) {
        if (requested != null && !requested.isBlank()) {
            AccountRules.requireLoginName(requested);
            if (accounts.existsByLoginName(requested)) {
                throw new BizException(ErrorCode.CONFLICT, "登录名已存在：" + requested);
            }
            return requested;
        }
        if (empNo == null || empNo.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "人员缺工号，无法按规则生成登录名");
        }
        return LoginNameRule.generate(properties.getProvision().getLoginNameRule(), empNo,
                accounts::existsByLoginName);
    }

    private Account requireAccount(String accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "账号不存在：" + accountId));
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(random.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    private static AccountSessionView toSessionView(KeycloakSessionView session) {
        return new AccountSessionView(session.id(), session.ipAddress(),
                Instant.ofEpochMilli(session.start()), Instant.ofEpochMilli(session.lastAccess()));
    }
}
