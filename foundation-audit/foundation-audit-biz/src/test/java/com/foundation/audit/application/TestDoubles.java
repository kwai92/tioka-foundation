package com.foundation.audit.application;

import com.foundation.account.api.AccountApi;
import com.foundation.account.api.dto.AccountCreateCmd;
import com.foundation.account.api.dto.AccountReconcileView;
import com.foundation.account.api.dto.AccountResetResult;
import com.foundation.account.api.dto.AccountSessionView;
import com.foundation.account.api.dto.AccountStatus;
import com.foundation.account.api.dto.AccountType;
import com.foundation.account.api.dto.AccountView;
import com.foundation.account.api.dto.EmergencyEnableTicket;
import com.foundation.account.api.dto.PageResult;
import com.foundation.account.api.dto.ServiceAccountCreateCmd;
import com.foundation.audit.api.AuditQuery;
import com.foundation.audit.api.LoginLogFailureStat;
import com.foundation.audit.api.LoginLogQuery;
import com.foundation.audit.domain.AuditLog;
import com.foundation.audit.domain.AuditSeal;
import com.foundation.audit.domain.LoginLog;
import com.foundation.audit.infrastructure.AuditArchiveStore;
import com.foundation.audit.infrastructure.AuditLogRepository;
import com.foundation.audit.infrastructure.AuditReportReceiptRepository;
import com.foundation.audit.infrastructure.AuditSealRepository;
import com.foundation.audit.infrastructure.LoginLogRepository;
import com.foundation.common.api.keycloak.KeycloakLoginEvent;
import com.foundation.common.api.keycloak.KeycloakLoginEventAdmin;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** F-09 BAS-10 审计与登录日志应用层单测用的内存仓储与桩（对齐 foundation-notify / foundation-account 的测试风格）。 */
final class TestDoubles {

    private TestDoubles() {
    }

    /** 构造仅回一个实例（或空）的 {@link ObjectProvider}，用于替代 Spring 容器注入。 */
    static <T> ObjectProvider<T> provider(T instance) {
        return new ObjectProvider<>() {
            @Override
            public T getObject() throws BeansException {
                if (instance == null) {
                    throw new NoSuchBeanDefinitionException("no bean available");
                }
                return instance;
            }

            @Override
            public T getObject(Object... args) throws BeansException {
                return getObject();
            }

            @Override
            public T getIfAvailable() {
                return instance;
            }

            @Override
            public T getIfUnique() {
                return instance;
            }
        };
    }

    static final class InMemoryAuditLogRepository implements AuditLogRepository {

        final List<AuditLog> rows = new ArrayList<>();

        List<AuditLog> byAction(String action) {
            return rows.stream().filter(row -> action.equals(row.action())).toList();
        }

        @Override
        public void save(AuditLog log) {
            rows.add(log);
        }

        @Override
        public List<AuditLog> page(java.util.Set<String> apps, AuditQuery query) {
            int size = query.size() <= 0 ? 20 : Math.min(query.size(), 100);
            Collection<String> effective = effectiveApps(apps, query);
            if (effective != null && effective.isEmpty()) {
                return List.of();
            }
            return rows.stream()
                    .filter(row -> matches(row, effective, query))
                    .filter(row -> beforeCursor(row, query.cursor()))
                    .sorted(Comparator.comparing(AuditLog::ts).thenComparing(AuditLog::id).reversed())
                    .limit(size)
                    .toList();
        }

        @Override
        public List<AuditLog> listForExport(java.util.Set<String> apps, AuditQuery query, int limit) {
            Collection<String> effective = effectiveApps(apps, query);
            if (effective != null && effective.isEmpty()) {
                return List.of();
            }
            return rows.stream()
                    .filter(row -> matches(row, effective, query))
                    .sorted(Comparator.comparing(AuditLog::ts).thenComparing(AuditLog::id).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<AuditLog> listByWindow(Instant from, Instant to) {
            return rows.stream()
                    .filter(row -> (from == null || !row.ts().isBefore(from)))
                    .filter(row -> (to == null || row.ts().isBefore(to)))
                    .sorted(Comparator.comparing(AuditLog::id))
                    .toList();
        }

        private static Collection<String> effectiveApps(java.util.Set<String> apps, AuditQuery query) {
            String filter = query == null || query.app() == null || query.app().isBlank() ? null : query.app();
            if (apps == null) {
                return filter == null ? null : List.of(filter);
            }
            if (filter == null) {
                return apps;
            }
            return apps.contains(filter) ? List.of(filter) : List.of();
        }

        private static boolean matches(AuditLog row, Collection<String> effective, AuditQuery query) {
            if (effective != null && !effective.contains(row.appCode())) {
                return false;
            }
            if (query.actorId() != null && !query.actorId().equals(row.actorId())) {
                return false;
            }
            if (query.action() != null && !query.action().equals(row.action())) {
                return false;
            }
            if (query.outcome() != null && !query.outcome().equalsIgnoreCase(
                    row.outcome() == null ? null : row.outcome().name())) {
                return false;
            }
            if (query.from() != null && row.ts().isBefore(query.from())) {
                return false;
            }
            return query.to() == null || row.ts().isBefore(query.to());
        }

        private static boolean beforeCursor(AuditLog row, String cursor) {
            if (cursor == null || cursor.isBlank()) {
                return true;
            }
            int idx = cursor.indexOf('|');
            Instant cursorTs = Instant.ofEpochMilli(Long.parseLong(cursor.substring(0, idx)));
            String cursorId = cursor.substring(idx + 1);
            int compared = row.ts().compareTo(cursorTs);
            return compared != 0 ? compared < 0 : row.id().compareTo(cursorId) < 0;
        }
    }

    static final class InMemoryLoginLogRepository implements LoginLogRepository {

        final List<LoginLog> rows = new ArrayList<>();

        @Override
        public boolean saveIfAbsent(LoginLog log) {
            boolean exists = rows.stream().anyMatch(row -> row.eventId().equals(log.eventId()));
            if (exists) {
                return false;
            }
            rows.add(log);
            return true;
        }

        @Override
        public List<LoginLog> page(LoginLogQuery query) {
            int page = query.page() <= 0 ? 1 : query.page();
            int size = query.size() <= 0 ? 20 : Math.min(query.size(), 100);
            return filter(query).stream()
                    .sorted(Comparator.comparing(LoginLog::ts).reversed())
                    .skip((long) (page - 1) * size)
                    .limit(size)
                    .toList();
        }

        @Override
        public long count(LoginLogQuery query) {
            return filter(query).size();
        }

        @Override
        public List<LoginLogFailureStat> failureStats(String loginName, Instant from, Instant to) {
            Map<String, Long> counts = new LinkedHashMap<>();
            for (LoginLog row : rows) {
                if (row.success() || row.loginName() == null) {
                    continue;
                }
                if (loginName != null && !loginName.isBlank() && !loginName.equals(row.loginName())) {
                    continue;
                }
                if (from != null && row.ts().isBefore(from)) {
                    continue;
                }
                if (to != null && !row.ts().isBefore(to)) {
                    continue;
                }
                counts.merge(row.loginName(), 1L, Long::sum);
            }
            return counts.entrySet().stream()
                    .map(entry -> new LoginLogFailureStat(entry.getKey(), entry.getValue()))
                    .toList();
        }

        @Override
        public Optional<Instant> latestTs() {
            return rows.stream().map(LoginLog::ts).max(Comparator.naturalOrder());
        }

        private List<LoginLog> filter(LoginLogQuery query) {
            return rows.stream()
                    .filter(row -> query.loginName() == null || query.loginName().equals(row.loginName()))
                    .filter(row -> query.personId() == null || query.personId().equals(row.personId()))
                    .filter(row -> query.success() == null || query.success() == row.success())
                    .filter(row -> query.from() == null || !row.ts().isBefore(query.from()))
                    .filter(row -> query.to() == null || row.ts().isBefore(query.to()))
                    .toList();
        }
    }

    static final class InMemoryAuditSealRepository implements AuditSealRepository {

        final List<AuditSeal> seals = new ArrayList<>();

        @Override
        public void save(AuditSeal seal) {
            seals.add(seal);
        }

        @Override
        public Optional<AuditSeal> latest() {
            return seals.stream().max(Comparator.comparing(AuditSeal::windowTo));
        }
    }

    static final class InMemoryAuditReportReceiptRepository implements AuditReportReceiptRepository {

        final Map<String, Integer> accepted = new LinkedHashMap<>();

        @Override
        public Optional<Integer> findAccepted(String idempotencyKey) {
            return Optional.ofNullable(accepted.get(idempotencyKey));
        }

        @Override
        public void save(String idempotencyKey, String appCode, int count) {
            accepted.put(idempotencyKey, count);
        }
    }

    static final class InMemoryArchiveStore implements AuditArchiveStore {

        final List<String> expired = new ArrayList<>();
        final List<String> dropped = new ArrayList<>();

        @Override
        public List<String> listExpiredPartitions(Instant cutoff) {
            return List.copyOf(expired);
        }

        @Override
        public void detachAndDrop(String partition) {
            dropped.add(partition);
        }
    }

    static final class StubKeycloakLoginEventAdmin implements KeycloakLoginEventAdmin {

        final List<KeycloakLoginEvent> events = new ArrayList<>();
        Instant lastSince;
        int lastMax;

        @Override
        public List<KeycloakLoginEvent> listLoginEvents(Instant since, int max) {
            lastSince = since;
            lastMax = max;
            return List.copyOf(events);
        }
    }

    static final class StubAccountApi implements AccountApi {

        final Map<String, AccountView> byLoginName = new LinkedHashMap<>();
        final List<String> recordedLogins = new ArrayList<>();
        final Map<String, Instant> lastLoginAt = new LinkedHashMap<>();

        @Override
        public Optional<AccountView> findByLoginName(String loginName) {
            return Optional.ofNullable(byLoginName.get(loginName));
        }

        @Override
        public void recordLogin(String loginName, Instant at) {
            recordedLogins.add(loginName);
            lastLoginAt.put(loginName, at);
        }

        @Override
        public Optional<AccountView> findById(String accountId) {
            return Optional.empty();
        }

        @Override
        public Optional<AccountView> findByPersonId(String personId) {
            return Optional.empty();
        }

        @Override
        public PageResult<AccountView> search(String q, AccountStatus status, AccountType type, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String createPersonal(AccountCreateCmd cmd) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void disable(String accountId, String operator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void enable(String accountId, String operator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void lock(String accountId, String operator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unlock(String accountId, String operator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AccountResetResult resetPassword(String accountId, String operator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AccountSessionView> sessions(String accountId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void revokeSessions(String accountId, String operator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EmergencyEnableTicket requestEmergencyEnable(String accountId, String operator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void confirmEmergencyEnable(String accountId, String requestId, String operator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String createServiceAccount(ServiceAccountCreateCmd cmd) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AccountView> listServiceAccounts(String appCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AccountView> serviceAccountsByOwner(String ownerPersonId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void disableServiceAccount(String accountId, String operator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void enableServiceAccount(String accountId, String operator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AccountReconcileView> recentReconciles(int limit) {
            throw new UnsupportedOperationException();
        }
    }
}
