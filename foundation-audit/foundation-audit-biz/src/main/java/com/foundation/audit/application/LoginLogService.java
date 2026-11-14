package com.foundation.audit.application;

import com.foundation.account.api.AccountApi;
import com.foundation.account.api.dto.AccountView;
import com.foundation.audit.api.LoginLogApi;
import com.foundation.audit.api.LoginLogFailureStat;
import com.foundation.audit.api.LoginLogPageView;
import com.foundation.audit.api.LoginLogQuery;
import com.foundation.audit.api.LoginLogView;
import com.foundation.audit.config.AuditProperties;
import com.foundation.audit.domain.LoginLog;
import com.foundation.audit.infrastructure.LoginLogRepository;
import com.foundation.common.api.keycloak.KeycloakLoginEvent;
import com.foundation.common.api.keycloak.KeycloakLoginEventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 登录日志应用服务（F-09 BAS-10 §登录日志）：从 Keycloak 采集、分页查询与失败统计。
 *
 * <p>{@link KeycloakLoginEventAdmin} 与 {@link AccountApi} 均可选：缺失时 {@link #collect()} 降级为不采集（记 warn），
 * 查询与统计不受影响。采集按 {@code event_id} 幂等，成功事件回填账号 {@code last_login_at}。
 */
public class LoginLogService implements LoginLogApi {

    private static final Logger log = LoggerFactory.getLogger(LoginLogService.class);

    /** 单次采集上限。 */
    private static final int COLLECT_BATCH = 500;

    private final LoginLogRepository logs;
    private final KeycloakLoginEventAdmin source;
    private final AccountApi accounts;
    private final AuditProperties properties;

    public LoginLogService(LoginLogRepository logs, ObjectProvider<KeycloakLoginEventAdmin> sourceProvider,
                           ObjectProvider<AccountApi> accountsProvider, AuditProperties properties) {
        this.logs = logs;
        this.source = sourceProvider.getIfAvailable();
        this.accounts = accountsProvider.getIfAvailable();
        this.properties = properties;
    }

    @Override
    @Transactional
    public int collect() {
        if (source == null) {
            log.warn("未装配 KeycloakLoginEventAdmin，登录日志采集降级为跳过");
            return 0;
        }
        Instant since = logs.latestTs().orElse(Instant.now().minus(Duration.ofHours(1)));
        List<KeycloakLoginEvent> events = source.listLoginEvents(since, COLLECT_BATCH);
        int added = 0;
        Map<String, Instant> lastLoginByUser = new HashMap<>();
        for (KeycloakLoginEvent event : events) {
            LoginLog entry = LoginLog.fromEvent(event);
            if (accounts != null && entry.loginName() != null) {
                Optional<AccountView> account = accounts.findByLoginName(entry.loginName());
                if (account.isPresent()) {
                    entry = entry.withAccount(account.get().id(), account.get().personId());
                }
            }
            if (logs.saveIfAbsent(entry)) {
                added++;
            }
            if (KeycloakLoginEvent.TYPE_LOGIN.equals(event.type()) && entry.loginName() != null
                    && event.occurredAt() != null) {
                lastLoginByUser.merge(entry.loginName(), event.occurredAt(),
                        (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        if (accounts != null) {
            lastLoginByUser.forEach(accounts::recordLogin);
        }
        return added;
    }

    @Override
    public LoginLogPageView page(LoginLogQuery query) {
        int page = query.page() <= 0 ? 1 : query.page();
        int size = query.size() <= 0 ? 20 : Math.min(query.size(), 100);
        List<LoginLogView> items = logs.page(query).stream().map(LoginLogService::toView).toList();
        long total = logs.count(query);
        return new LoginLogPageView(items, total, page, size);
    }

    @Override
    public List<LoginLogFailureStat> failureStats(String loginName, Instant from, Instant to) {
        return logs.failureStats(loginName, from, to);
    }

    private static LoginLogView toView(LoginLog log) {
        return new LoginLogView(log.id(), log.loginName(), log.personId(), log.accountId(),
                log.keycloakUserId(), log.success(), log.authMethod(), log.clientId(), log.failureReason(),
                log.clientIp(), log.userAgent(), log.ts());
    }
}
