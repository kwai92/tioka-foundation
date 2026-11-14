package com.foundation.account.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.account.domain.Account;
import com.foundation.account.domain.AccountIdPrefix;
import com.foundation.account.domain.AccountPushRecord;
import com.foundation.account.domain.PushAction;
import com.foundation.account.infrastructure.AccountPushRepository;
import com.foundation.account.infrastructure.AccountRepository;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.api.keycloak.KeycloakUserAdmin;
import com.foundation.common.api.keycloak.KeycloakUserSpec;
import com.foundation.common.id.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 账号写操作推送 Keycloak（F-04 §5）。
 *
 * <p>策略：**先落 Foundation 库，再推送 Keycloak**；立即推送失败即写 {@code fdn_account_push} 待发记录，
 * 由 {@link AccountReconcileJob} 每日兜底重试。明文口令与重置链接不落库、不进日志：{@code payloadJson}
 * 只含非敏感推送参数。
 */
public class AccountPushService {

    private static final Logger log = LoggerFactory.getLogger(AccountPushService.class);

    /** 推送失败重试次数上限，超过即置终态 {@code FAILED}。 */
    private static final int MAX_ATTEMPTS = 8;

    /** 单次重试的退避上限（秒）。 */
    private static final long MAX_BACKOFF_SECONDS = 3600L;

    private static final int MAX_ERROR_LENGTH = 512;

    private final AccountPushRepository pushes;
    private final AccountRepository accounts;
    private final KeycloakUserAdmin keycloak;
    private final ObjectMapper json;

    public AccountPushService(AccountPushRepository pushes, AccountRepository accounts,
                              KeycloakUserAdmin keycloak, ObjectMapper json) {
        this.pushes = pushes;
        this.accounts = accounts;
        this.keycloak = keycloak;
        this.json = json;
    }

    /**
     * 立即推送；成功返回 {@code true}，失败写入待发记录并返回 {@code false}（对账任务兜底重试）。
     */
    public boolean push(String accountId, PushAction action, Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        try {
            apply(accountId, action, safe);
            return true;
        } catch (RuntimeException ex) {
            pushes.insert(AccountPushRecord.pending(Ids.newId(AccountIdPrefix.PUSH), accountId, action, writeJson(safe)));
            log.warn("账号推送 Keycloak 失败，已入待发记录：account={} action={} err={}",
                    accountId, action, ex.getMessage());
            return false;
        }
    }

    /** 重试到期的待发记录，返回处理条数（成功与失败均计入）。 */
    public int flushDue(int limit) {
        List<AccountPushRecord> due = pushes.findDue(Instant.now(), limit);
        for (AccountPushRecord record : due) {
            attempt(record);
        }
        return due.size();
    }

    private void attempt(AccountPushRecord record) {
        try {
            apply(record.accountId(), record.action(), readJson(record.payloadJson()));
            pushes.markSent(record.id());
        } catch (RuntimeException ex) {
            int attempts = record.attempts() + 1;
            boolean terminal = attempts >= MAX_ATTEMPTS;
            pushes.markFailed(record.id(), truncate(ex.getMessage()),
                    terminal ? null : Instant.now().plusSeconds(backoffSeconds(attempts)));
            log.warn("账号待发记录重试失败：id={} action={} attempts={} terminal={} err={}",
                    record.id(), record.action(), attempts, terminal, ex.getMessage());
        }
    }

    private void apply(String accountId, PushAction action, Map<String, Object> payload) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "账号不存在：" + accountId));
        switch (action) {
            case CREATE_USER -> createUser(account, payload);
            case SET_ENABLED -> keycloak.setEnabled(requireKeycloakUserId(account),
                    Boolean.parseBoolean(String.valueOf(payload.get("enabled"))));
            case SET_REQUIRED_ACTIONS -> keycloak.setRequiredActions(requireKeycloakUserId(account),
                    stringList(payload.get("requiredActions")));
            case RESET_PASSWORD -> keycloak.sendPasswordResetEmail(requireKeycloakUserId(account));
            case REVOKE_SESSIONS -> keycloak.logoutSessions(requireKeycloakUserId(account));
        }
    }

    private void createUser(Account account, Map<String, Object> payload) {
        if (account.keycloakUserId() != null && !account.keycloakUserId().isBlank()) {
            return;
        }
        KeycloakUserSpec spec = new KeycloakUserSpec(
                account.loginName(),
                stringOrNull(payload.get("email")),
                stringOrNull(payload.get("firstName")),
                stringOrNull(payload.get("lastName")),
                !payload.containsKey("enabled") || Boolean.parseBoolean(String.valueOf(payload.get("enabled"))),
                stringList(payload.get("requiredActions")));
        String keycloakUserId = keycloak.createUser(spec);
        accounts.update(account.withKeycloakUserId(keycloakUserId));
    }

    private static String requireKeycloakUserId(Account account) {
        if (account.keycloakUserId() == null || account.keycloakUserId().isBlank()) {
            throw new BizException(ErrorCode.CONFLICT, "账号尚未推送 Keycloak，无法执行该操作：" + account.id());
        }
        return account.keycloakUserId();
    }

    private static long backoffSeconds(int attempts) {
        long seconds = 1L << Math.min(attempts, 12);
        return Math.min(seconds, MAX_BACKOFF_SECONDS);
    }

    private String writeJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("账号推送参数序列化失败", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(text, Map.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("账号推送参数反序列化失败", ex);
        }
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
