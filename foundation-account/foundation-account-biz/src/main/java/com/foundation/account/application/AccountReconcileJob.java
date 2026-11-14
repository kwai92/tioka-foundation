package com.foundation.account.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.account.api.dto.AccountType;
import com.foundation.account.config.AccountProperties;
import com.foundation.account.domain.Account;
import com.foundation.account.domain.AccountIdPrefix;
import com.foundation.account.domain.AccountReconcileRecord;
import com.foundation.account.domain.DriftItem;
import com.foundation.account.domain.FoundationAccountSnapshot;
import com.foundation.account.domain.KeycloakAccountSnapshot;
import com.foundation.account.domain.ReconcileDiff;
import com.foundation.account.infrastructure.AccountReconcileLock;
import com.foundation.account.infrastructure.AccountReconcileRepository;
import com.foundation.account.infrastructure.AccountRepository;
import com.foundation.common.api.keycloak.KeycloakUserAdmin;
import com.foundation.common.api.keycloak.KeycloakUserView;
import com.foundation.common.id.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 每日对账（F-04 §6）：发现并修复 Keycloak 侧的人为改动，以 Foundation 为准回正。
 *
 * <p>单实例执行（{@link AccountReconcileLock}）；比对存在性、{@code enabled}、{@code username}、必需动作；
 * 差异超阈值（{@code drift-threshold-percent} / {@code drift-threshold-count}）时**暂停修复并告警**；
 * 每次执行写 {@link AccountReconcileRecord}（{@code fdn_account_reconcile}）。
 *
 * <p>期望的必需动作只对**未登录过的可登录账号**（个人 / 应急管理员）生效：一旦账号完成首次登录
 * （{@code last_login_at} 由 BAS-10 回填），首次改密等一次性动作即不再期望，避免每日重复添加。
 */
public class AccountReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(AccountReconcileJob.class);

    private final AccountRepository accounts;
    private final KeycloakUserAdmin keycloak;
    private final AccountPushService pushService;
    private final AccountReconcileRepository reconciles;
    private final AccountReconcileLock lock;
    private final AccountProperties properties;
    private final ObjectMapper json;

    public AccountReconcileJob(AccountRepository accounts, KeycloakUserAdmin keycloak,
                               AccountPushService pushService, AccountReconcileRepository reconciles,
                               AccountReconcileLock lock, AccountProperties properties, ObjectMapper json) {
        this.accounts = accounts;
        this.keycloak = keycloak;
        this.pushService = pushService;
        this.reconciles = reconciles;
        this.lock = lock;
        this.properties = properties;
        this.json = json;
    }

    /** 每日计划入口（计划由 {@code foundation.account.reconcile.cron} 配置）。 */
    @Scheduled(cron = "${foundation.account.reconcile.cron:0 30 2 * * *}")
    public void runDaily() {
        reconcileOnce();
    }

    /**
     * 执行一轮对账（供定时任务与手动触发）。
     *
     * @return 本轮对账记录；未能取得单实例锁（其他实例在跑）时返回 {@code null}
     */
    public AccountReconcileRecord reconcileOnce() {
        String token = lock.tryLock();
        if (token == null) {
            log.info("账号每日对账：其他实例持有锁，跳过本轮");
            return null;
        }
        try {
            return doReconcile();
        } finally {
            lock.unlock(token);
        }
    }

    private AccountReconcileRecord doReconcile() {
        List<Account> all = accounts.findAll();
        Map<String, FoundationAccountSnapshot> byId = new HashMap<>();
        List<FoundationAccountSnapshot> foundation = new ArrayList<>(all.size());
        for (Account account : all) {
            FoundationAccountSnapshot snapshot = FoundationAccountSnapshot.of(account, expectedActions(account));
            foundation.add(snapshot);
            byId.put(account.id(), snapshot);
        }
        List<KeycloakAccountSnapshot> keycloakUsers = keycloak.listUsers().stream()
                .map(AccountReconcileJob::toSnapshot)
                .toList();

        ReconcileDiff diff = ReconcileDiff.compute(foundation, keycloakUsers);
        AccountProperties.Reconcile config = properties.getReconcile();
        boolean held = diff.exceedsThreshold(all.size(),
                config.getDriftThresholdPercent(), config.getDriftThresholdCount());

        int fixed = 0;
        if (held) {
            if (config.isAlertOnHold()) {
                log.warn("账号每日对账差异超阈值，已暂停自动修复：total={} drifted={} thresholdPercent={} "
                                + "thresholdCount={}", all.size(), diff.drifted(),
                        config.getDriftThresholdPercent(), config.getDriftThresholdCount());
            }
        } else {
            fixed = fix(diff, byId);
        }
        // 待发记录兜底重试（含尚未推送 Keycloak 的账号：由推送补齐缺失用户）
        pushService.flushDue(config.getPushFlushLimit());

        AccountReconcileRecord record = new AccountReconcileRecord(Ids.newId(AccountIdPrefix.RECONCILE),
                Instant.now(), all.size(), diff.drifted(), fixed, held, writeDetail(diff));
        reconciles.insert(record);
        log.info("账号每日对账完成：total={} drifted={} fixed={} held={}",
                all.size(), diff.drifted(), fixed, held);
        return record;
    }

    private int fix(ReconcileDiff diff, Map<String, FoundationAccountSnapshot> snapshots) {
        int fixed = 0;
        for (DriftItem item : diff.fixable()) {
            try {
                if (applyFix(item, snapshots)) {
                    fixed++;
                }
            } catch (RuntimeException ex) {
                log.warn("账号对账修复失败：kind={} account={} login={} err={}",
                        item.kind(), item.accountId(), item.loginName(), ex.getMessage());
            }
        }
        return fixed;
    }

    /** 以 Foundation 快照为准回正一条差异；返回是否已修复。 */
    private boolean applyFix(DriftItem item, Map<String, FoundationAccountSnapshot> snapshots) {
        FoundationAccountSnapshot snapshot = snapshots.get(item.accountId());
        switch (item.kind()) {
            case ENABLED_MISMATCH: {
                keycloak.setEnabled(requireKeycloakUserId(snapshot), snapshot.expectedEnabled());
                return true;
            }
            case USERNAME_MISMATCH: {
                keycloak.setUsername(requireKeycloakUserId(snapshot), snapshot.loginName());
                return true;
            }
            case REQUIRED_ACTIONS_MISMATCH: {
                keycloak.setRequiredActions(requireKeycloakUserId(snapshot), snapshot.requiredActions());
                return true;
            }
            case ORPHAN_IN_KEYCLOAK: {
                // 非 Foundation 管理：标记（写入明细）并停用，不直接删除
                KeycloakUserView orphan = keycloak.findByUsername(item.loginName());
                if (orphan == null) {
                    return false;
                }
                keycloak.setEnabled(orphan.id(), false);
                return true;
            }
            case MISSING_IN_KEYCLOAK:
            default:
                // 缺失用户交由推送重试补齐（MISSING 不可在本轮就地修复）
                return false;
        }
    }

    /**
     * 期望的必需动作（F-04 §6）：仅对未登录过的可登录账号（个人 / 应急管理员）期望首次改密等动作；
     * 服务账号与已完成首次登录的账号期望为空。
     */
    private List<String> expectedActions(Account account) {
        if (account.type() == AccountType.SERVICE || account.lastLoginAt() != null) {
            return List.of();
        }
        return properties.getProvision().getRequiredActions();
    }

    private static String requireKeycloakUserId(FoundationAccountSnapshot snapshot) {
        String keycloakUserId = snapshot == null ? null : snapshot.keycloakUserId();
        if (keycloakUserId == null || keycloakUserId.isBlank()) {
            throw new IllegalStateException("对账修复缺少 Keycloak 用户 ID");
        }
        return keycloakUserId;
    }

    private static KeycloakAccountSnapshot toSnapshot(KeycloakUserView user) {
        return new KeycloakAccountSnapshot(user.id(), user.username(), user.enabled(), user.requiredActions());
    }

    private String writeDetail(ReconcileDiff diff) {
        if (diff.drifts().isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(diff.drifts());
        } catch (JsonProcessingException ex) {
            log.warn("账号对账明细序列化失败：{}", ex.getMessage());
            return null;
        }
    }
}
