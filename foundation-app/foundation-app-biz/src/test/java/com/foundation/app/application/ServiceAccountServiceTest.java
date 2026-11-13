package com.foundation.app.application;

import com.foundation.app.api.ServiceAccountCmd;
import com.foundation.app.api.ServiceAccountView;
import com.foundation.app.application.TestDoubles.InMemoryAppRepository;
import com.foundation.app.application.TestDoubles.InMemoryServiceAccountRepository;
import com.foundation.app.application.TestDoubles.RecordingKeycloak;
import com.foundation.app.config.AppProperties;
import com.foundation.app.domain.App;
import com.foundation.app.domain.AppKind;
import com.foundation.app.domain.ServiceAccount;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.api.keycloak.OidcGrantTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-05 BAS-04：服务账号只走 client_credentials，client_id 按模板生成，权限码白名单落库。 */
class ServiceAccountServiceTest {

    private final InMemoryAppRepository apps = new InMemoryAppRepository();
    private final InMemoryServiceAccountRepository accounts = new InMemoryServiceAccountRepository();
    private final RecordingKeycloak keycloak = new RecordingKeycloak();
    private final AppProperties properties = new AppProperties();

    private ServiceAccountService service() {
        return new ServiceAccountService(apps, accounts, keycloak, properties);
    }

    @BeforeEach
    void seedApp() {
        apps.insert(App.register("app_1", "order-center", "订单中心", null, null, AppKind.SUB, "u1"), "alice");
    }

    private static ServiceAccountCmd cmd(String name, List<String> permissionCodes, Instant expiresAt) {
        return new ServiceAccountCmd("order-center", "acc_1", name, permissionCodes, "u1", expiresAt, "alice");
    }

    @Test
    void createProvisionClientCredentialsClientByPattern() {
        String id = service().create(cmd("订单同步", List.of("fdn:app:read"), null));

        assertThat(id).startsWith("svc_");
        ServiceAccount account = accounts.byId.get(id);
        assertThat(account.clientId()).isEqualTo("order-center-sa");
        assertThat(account.permissionCodes()).containsExactly("fdn:app:read");
        assertThat(account.status()).isEqualTo(ServiceAccount.STATUS_ACTIVE);

        assertThat(keycloak.upserts).hasSize(1);
        assertThat(keycloak.upserts.get(0).clientId()).isEqualTo("order-center-sa");
        assertThat(keycloak.upserts.get(0).grantTypes()).containsExactly(OidcGrantTypes.CLIENT_CREDENTIALS);
        assertThat(keycloak.upserts.get(0).redirectUris()).isEmpty();
    }

    @Test
    void createHonoursCustomClientIdPattern() {
        properties.setServiceAccountClientIdPattern("{appCode}__bot");

        String id = service().create(cmd("订单同步", List.of(), null));

        assertThat(accounts.byId.get(id).clientId()).isEqualTo("order-center__bot");
    }

    @Test
    void createRejectsDuplicateNameWithinApp() {
        service().create(cmd("订单同步", List.of(), null));

        assertThatThrownBy(() -> service().create(cmd("订单同步", List.of(), null)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        assertThat(keycloak.upserts).hasSize(1);
    }

    @Test
    void createRejectsUnknownApp() {
        assertThatThrownBy(() -> service().create(new ServiceAccountCmd("missing", "acc_1", "订单同步",
                List.of(), "u1", null, "alice")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void createRequiresBoundAccountSubject() {
        assertThatThrownBy(() -> service().create(new ServiceAccountCmd("order-center", "  ", "订单同步",
                List.of(), "u1", null, "alice")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void disableMarksAccountDisabled() {
        String id = service().create(cmd("订单同步", List.of(), null));

        service().disable(id, "bob");

        assertThat(accounts.byId.get(id).status()).isEqualTo(ServiceAccount.STATUS_DISABLED);
    }

    @Test
    void disableRejectsUnknownAccount() {
        assertThatThrownBy(() -> service().disable("svc_missing", "bob"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void disableExpiredOnlyTouchesExpiredActiveAccounts() {
        String expiredId = service().create(cmd("已过期", List.of(), Instant.now().minusSeconds(60)));
        String keepId = service().create(cmd("不过期", List.of(), null));

        int affected = service().disableExpired(Instant.now());

        assertThat(affected).isEqualTo(1);
        assertThat(accounts.byId.get(expiredId).status()).isEqualTo(ServiceAccount.STATUS_DISABLED);
        assertThat(accounts.byId.get(keepId).status()).isEqualTo(ServiceAccount.STATUS_ACTIVE);
    }

    @Test
    void listByAppReturnsServiceAccountViews() {
        service().create(cmd("订单同步", List.of("fdn:app:read"), null));

        List<ServiceAccountView> views = service().listByApp("order-center");

        assertThat(views).hasSize(1);
        assertThat(views.get(0).name()).isEqualTo("订单同步");
        assertThat(views.get(0).clientId()).isEqualTo("order-center-sa");
        assertThat(views.get(0).permissionCodes()).containsExactly("fdn:app:read");
    }
}
