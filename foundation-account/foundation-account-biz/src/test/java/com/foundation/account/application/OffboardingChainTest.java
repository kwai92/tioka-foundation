package com.foundation.account.application;

import com.foundation.account.api.dto.AccountStatus;
import com.foundation.account.api.dto.AccountType;
import com.foundation.account.api.dto.AccountView;
import com.foundation.account.api.dto.OffboardingResult;
import com.foundation.account.config.OffboardingProperties;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.org.api.PersonApi;
import com.foundation.org.api.PersonLifecycleApi;
import com.foundation.org.api.dto.PersonStatus;
import com.foundation.org.api.dto.PersonType;
import com.foundation.org.api.dto.PersonView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** {@link OffboardingApplicationService} 单测（F-04 BAS-14 §1 动作链、测试表 {@code OffboardingChainTest}）。 */
class OffboardingChainTest {

    private final PersonApi persons = mock(PersonApi.class);

    private final PersonLifecycleApi lifecycle = mock(PersonLifecycleApi.class);

    private final AccountApplicationService accounts = mock(AccountApplicationService.class);

    private final HandoverService handovers = mock(HandoverService.class);

    private final OffboardingProperties properties = new OffboardingProperties();

    private OffboardingApplicationService service;

    @BeforeEach
    void setUp() {
        service = new OffboardingApplicationService(persons, lifecycle, accounts, handovers, properties);
    }

    @Test
    void offboardRunsAccountThenHandoverThenPersonInOrder() {
        when(persons.findById("per_1")).thenReturn(Optional.of(person("per_1")));
        when(accounts.findByPersonId("per_1")).thenReturn(Optional.of(account("acc_1")));
        when(accounts.findById("acc_1")).thenReturn(Optional.of(account("acc_1")));
        when(handovers.enqueue("per_1")).thenReturn(2);

        OffboardingResult result = service.offboard("per_1", "sys_admin");

        InOrder order = inOrder(accounts, handovers, lifecycle);
        order.verify(accounts).disable("acc_1", "sys_admin");
        order.verify(handovers).enqueue("per_1");
        order.verify(lifecycle).offboard("per_1", "sys_admin");

        assertThat(result.personId()).isEqualTo("per_1");
        assertThat(result.accountId()).isEqualTo("acc_1");
        assertThat(result.keycloakUserId()).isEqualTo("kc_1");
        assertThat(result.sessionsRevoked()).isTrue();
        assertThat(result.revokedAt()).isNotNull();
        assertThat(result.handoverCount()).isEqualTo(2);
    }

    @Test
    void stepIsRetriedThenSucceeds() {
        when(persons.findById("per_1")).thenReturn(Optional.of(person("per_1")));
        when(accounts.findByPersonId("per_1")).thenReturn(Optional.of(account("acc_1")));
        when(accounts.findById("acc_1")).thenReturn(Optional.of(account("acc_1")));
        doThrow(new IllegalStateException("keycloak 抖动"))
                .doThrow(new IllegalStateException("keycloak 抖动"))
                .doNothing()
                .when(accounts).disable("acc_1", "sys_admin");
        when(handovers.enqueue("per_1")).thenReturn(0);

        OffboardingResult result = service.offboard("per_1", "sys_admin");

        verify(accounts, times(3)).disable("acc_1", "sys_admin");
        assertThat(result.sessionsRevoked()).isTrue();
        verify(lifecycle).offboard("per_1", "sys_admin");
    }

    @Test
    void laterStepFailureKeepsSessionRevokeAndStopsChain() {
        when(persons.findById("per_1")).thenReturn(Optional.of(person("per_1")));
        when(accounts.findByPersonId("per_1")).thenReturn(Optional.of(account("acc_1")));
        when(handovers.enqueue("per_1")).thenThrow(new IllegalStateException("消息中心不可用"));

        assertThatThrownBy(() -> service.offboard("per_1", "sys_admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("消息中心不可用");

        // 会话吊销已提交，不被后续步骤失败回滚
        verify(accounts, times(1)).disable("acc_1", "sys_admin");
        // 交接待办按重试策略重试后仍失败
        verify(handovers, times(3)).enqueue("per_1");
        // 人员状态未被置为离职
        verify(lifecycle, never()).offboard(anyString(), anyString());
    }

    @Test
    void revokeOnDisableFalseDisablesWithoutRevokingSessions() {
        properties.setRevokeOnDisable(false);
        when(persons.findById("per_1")).thenReturn(Optional.of(person("per_1")));
        when(accounts.findByPersonId("per_1")).thenReturn(Optional.of(account("acc_1")));
        when(accounts.findById("acc_1")).thenReturn(Optional.of(account("acc_1")));
        when(handovers.enqueue("per_1")).thenReturn(0);

        OffboardingResult result = service.offboard("per_1", "sys_admin");

        verify(accounts).disableWithoutRevokingSessions("acc_1", "sys_admin");
        verify(accounts, never()).disable(anyString(), anyString());
        assertThat(result.sessionsRevoked()).isFalse();
        assertThat(result.revokedAt()).isNull();
    }

    @Test
    void personWithoutPersonalAccountStillOffboardsAndEnqueuesHandover() {
        when(persons.findById("per_1")).thenReturn(Optional.of(person("per_1")));
        when(accounts.findByPersonId("per_1")).thenReturn(Optional.empty());
        when(handovers.enqueue("per_1")).thenReturn(1);

        OffboardingResult result = service.offboard("per_1", "sys_admin");

        assertThat(result.accountId()).isNull();
        assertThat(result.keycloakUserId()).isNull();
        assertThat(result.sessionsRevoked()).isFalse();
        assertThat(result.handoverCount()).isEqualTo(1);
        verify(lifecycle).offboard("per_1", "sys_admin");
    }

    @Test
    void unknownPersonIsRejectedBeforeAnySideEffect() {
        when(persons.findById("per_x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.offboard("per_x", "sys_admin"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        verifyNoInteractions(accounts, handovers, lifecycle);
    }

    private static PersonView person(String id) {
        return new PersonView(id, "E001", "张三", PersonType.EMPLOYEE, PersonStatus.ACTIVE,
                null, null, false, null, null, 1L);
    }

    private static AccountView account(String id) {
        return new AccountView(id, "e001", "per_1", "kc_1", AccountStatus.NORMAL, AccountType.PERSONAL,
                null, 3L);
    }
}
