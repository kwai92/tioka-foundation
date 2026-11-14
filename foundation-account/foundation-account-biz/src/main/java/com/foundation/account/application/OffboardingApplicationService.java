package com.foundation.account.application;

import com.foundation.account.api.dto.AccountView;
import com.foundation.account.api.dto.OffboardingResult;
import com.foundation.account.config.OffboardingProperties;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.org.api.PersonApi;
import com.foundation.org.api.PersonLifecycleApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 停用与离职联动（F-04 BAS-14 §1）：动作链编排。
 *
 * <ol>
 *   <li>账号停用：{@code fdn_account.status=DISABLED} 并推 Keycloak {@code enabled=false}（BAS-03 事务边界）；</li>
 *   <li>Keycloak 会话吊销：由 BAS-03 停用入口连带执行，结果与时间记录在 {@link OffboardingResult}；</li>
 *   <li>交接待办：该人员负责的服务账号进入队列（{@link HandoverService}）；</li>
 *   <li>发事件：{@code fdn.account.sessions_revoked} 由停用步骤发出，
 *       {@code fdn.person.disabled}（{@code changeType=LEFT}）由人员离职步骤发出。</li>
 * </ol>
 *
 * <p><b>事务边界</b>：本类自身不开事务，每一步委托给各自的事务服务；因此后续步骤失败**不会回滚**已提交的
 * 会话吊销（F-04 §1）。每步失败重试 {@value #STEP_ATTEMPTS} 次（各步幂等，重试安全）。
 *
 * <p><b>事件去重</b>：两个事件分别由状态真正的属主域发出（账号域 / 人员域），本编排器不重复投递，
 * 消费端按事件 ID 幂等（S15 §14.2）。
 */
public class OffboardingApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OffboardingApplicationService.class);

    /** 单步失败的重试次数（F-04 §1「各步失败重试」）。 */
    private static final int STEP_ATTEMPTS = 3;

    private final PersonApi persons;
    private final PersonLifecycleApi lifecycle;
    private final AccountApplicationService accounts;
    private final HandoverService handovers;
    private final OffboardingProperties properties;

    public OffboardingApplicationService(PersonApi persons, PersonLifecycleApi lifecycle,
                                         AccountApplicationService accounts, HandoverService handovers,
                                         OffboardingProperties properties) {
        this.persons = persons;
        this.lifecycle = lifecycle;
        this.accounts = accounts;
        this.handovers = handovers;
        this.properties = properties;
    }

    /** 离职停用（F-04 §4 {@code POST /api/persons/{id}/offboard}）。 */
    public OffboardingResult offboard(String personId, String operator) {
        if (personId == null || personId.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "人员 ID 必填");
        }
        if (persons.findById(personId).isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "人员不存在：" + personId);
        }

        // ①+② 账号停用与会话吊销（BAS-03 事务边界，独立提交）
        String accountId = null;
        String keycloakUserId = null;
        boolean sessionsRevoked = false;
        Instant revokedAt = null;
        Optional<AccountView> account = accounts.findByPersonId(personId);
        if (account.isPresent()) {
            final String targetAccountId = account.get().id();
            accountId = targetAccountId;
            if (properties.isRevokeOnDisable()) {
                Instant startedAt = Instant.now();
                retry("账号停用与会话吊销", () -> accounts.disable(targetAccountId, operator));
                revokedAt = Instant.now();
                sessionsRevoked = true;
                warnIfBeyondDeadline(personId, startedAt, revokedAt);
            } else {
                retry("账号停用", () -> accounts.disableWithoutRevokingSessions(targetAccountId, operator));
            }
            keycloakUserId = accounts.findById(targetAccountId).map(AccountView::keycloakUserId).orElse(null);
        }

        // ③ 服务账号与负责事项进交接待办
        int handoverCount = retryCall("交接待办", () -> handovers.enqueue(personId));

        // ④ 人员离职：状态置 LEFT 并发 fdn.person.disabled（changeType=LEFT）
        retry("人员离职", () -> lifecycle.offboard(personId, operator));

        return new OffboardingResult(personId, accountId, keycloakUserId, sessionsRevoked, revokedAt,
                handoverCount);
    }

    private void warnIfBeyondDeadline(String personId, Instant startedAt, Instant revokedAt) {
        long deadlineMillis = properties.getSessionRevokeDeadlineSeconds() * 1000L;
        long elapsedMillis = Duration.between(startedAt, revokedAt).toMillis();
        if (elapsedMillis > deadlineMillis) {
            log.warn("会话吊销超出验收口径：person={} 用时={}ms 上限={}s",
                    personId, elapsedMillis, properties.getSessionRevokeDeadlineSeconds());
        }
    }

    private void retry(String step, Runnable action) {
        retryCall(step, () -> {
            action.run();
            return null;
        });
    }

    private <T> T retryCall(String step, Supplier<T> action) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= STEP_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException ex) {
                last = ex;
                log.warn("离职联动步骤失败（第 {}/{} 次）：step={} err={}",
                        attempt, STEP_ATTEMPTS, step, ex.getMessage());
            }
        }
        throw last;
    }
}
