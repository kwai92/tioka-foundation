package com.foundation.account.application;

import com.foundation.account.api.dto.HandoverView;
import com.foundation.account.config.OffboardingProperties;
import com.foundation.account.domain.HandoverStatus;
import com.foundation.account.domain.HandoverTodo;
import com.foundation.account.infrastructure.HandoverTodoRepository;
import com.foundation.app.api.AppApi;
import com.foundation.app.api.AppView;
import com.foundation.app.api.ServiceAccountApi;
import com.foundation.app.api.ServiceAccountView;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.org.api.PersonApi;
import com.foundation.org.api.dto.PersonStatus;
import com.foundation.org.api.dto.PersonType;
import com.foundation.org.api.dto.PersonView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link HandoverService} 单测（F-04 BAS-14 §1 第 3 步、§4 交接待办）。 */
class HandoverServiceTest {

    private static final String APP_CODE = "crm";

    private final InMemoryHandoverTodoRepository todos = new InMemoryHandoverTodoRepository();

    private final AppApi apps = mock(AppApi.class);

    private final ServiceAccountApi serviceAccounts = mock(ServiceAccountApi.class);

    private final PersonApi persons = mock(PersonApi.class);

    private final OffboardingProperties properties = new OffboardingProperties();

    private HandoverService service;

    @BeforeEach
    void setUp() {
        service = new HandoverService(todos, apps, serviceAccounts, persons, properties);
        when(apps.list()).thenReturn(List.of(app("app_1", APP_CODE)));
        when(serviceAccounts.listByApp(APP_CODE)).thenReturn(List.of(
                serviceAccount("svc_1", "acc_1", "per_1"),
                serviceAccount("svc_2", "acc_2", "per_2")));
    }

    @Test
    void enqueueCreatesPendingTodosOnlyForOwnedServiceAccountsAndIsIdempotent() {
        int first = service.enqueue("per_1");
        int second = service.enqueue("per_1");

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();

        List<HandoverView> queue = service.list(null);
        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).ownerPersonId()).isEqualTo("per_1");
        assertThat(queue.get(0).accountId()).isEqualTo("acc_1");
        assertThat(queue.get(0).serviceAccountId()).isEqualTo("svc_1");
        assertThat(queue.get(0).appCode()).isEqualTo(APP_CODE);
        assertThat(queue.get(0).status()).isEqualTo(HandoverStatus.PENDING.name());
        assertThat(service.list("per_2")).isEmpty();
    }

    @Test
    void enqueueSkippedWhenHandoverTodoDisabled() {
        properties.setCreateHandoverTodo(false);

        assertThat(service.enqueue("per_1")).isZero();
        assertThat(service.list(null)).isEmpty();
    }

    @Test
    void handoverTransfersOwnershipAndClosesTodo() {
        service.enqueue("per_1");
        when(persons.findById("per_9")).thenReturn(Optional.of(person("per_9")));

        HandoverView view = service.handover("acc_1", "per_9", "sys_admin");

        verify(serviceAccounts).transferOwner("svc_1", "per_9", "sys_admin");
        assertThat(view.status()).isEqualTo(HandoverStatus.HANDED_OVER.name());
        assertThat(view.newOwnerPersonId()).isEqualTo("per_9");
        assertThat(view.handledAt()).isNotNull();
        assertThat(service.list(null)).isEmpty();
    }

    @Test
    void handoverRejectsMissingNewOwnerAndUnknownTodo() {
        service.enqueue("per_1");
        when(persons.findById("per_404")).thenReturn(Optional.empty());
        when(persons.findById("per_9")).thenReturn(Optional.of(person("per_9")));

        assertThatThrownBy(() -> service.handover("acc_1", " ", "sys_admin"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> service.handover("acc_1", "per_404", "sys_admin"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> service.handover("acc_404", "per_9", "sys_admin"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private static AppView app(String id, String code) {
        return new AppView(id, code, "CRM", null, null, "PORTAL", "per_9", "ENABLED", 1L);
    }

    private static ServiceAccountView serviceAccount(String id, String accountId, String ownerPersonId) {
        return new ServiceAccountView(id, accountId, "app_1", "cli_" + id, "crm-" + id,
                List.of("crm:read"), ownerPersonId, "ACTIVE", null);
    }

    private static PersonView person(String id) {
        return new PersonView(id, "E00" + id, "李四", PersonType.EMPLOYEE, PersonStatus.ACTIVE,
                null, null, false, null, null, 1L);
    }

    /** 内存交接待办仓储。 */
    private static final class InMemoryHandoverTodoRepository implements HandoverTodoRepository {

        private final Map<String, HandoverTodo> byId = new LinkedHashMap<>();

        @Override
        public void insert(HandoverTodo todo) {
            byId.put(todo.id(), todo);
        }

        @Override
        public Optional<HandoverTodo> findPendingByAccountId(String accountId) {
            return byId.values().stream()
                    .filter(todo -> todo.status() == HandoverStatus.PENDING)
                    .filter(todo -> todo.accountId().equals(accountId))
                    .findFirst();
        }

        @Override
        public List<HandoverTodo> findPending(String ownerPersonId) {
            return byId.values().stream()
                    .filter(todo -> todo.status() == HandoverStatus.PENDING)
                    .filter(todo -> ownerPersonId == null || ownerPersonId.isBlank()
                            || ownerPersonId.equals(todo.ownerPersonId()))
                    .toList();
        }

        @Override
        public void markHandedOver(String id, String newOwnerPersonId, Instant handledAt) {
            byId.put(id, byId.get(id).handedOver(newOwnerPersonId, handledAt));
        }
    }
}
