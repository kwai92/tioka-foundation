package com.foundation.app.application;

import com.foundation.app.api.AppRegistrationCmd;
import com.foundation.app.api.AppUpdateCmd;
import com.foundation.app.api.AppView;
import com.foundation.app.application.TestDoubles.InMemoryAppClientRepository;
import com.foundation.app.application.TestDoubles.InMemoryAppRepository;
import com.foundation.app.application.TestDoubles.RecordingKeycloak;
import com.foundation.app.domain.App;
import com.foundation.app.domain.AppClient;
import com.foundation.app.domain.AppKind;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.api.keycloak.OidcGrantTypes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-05 BAS-04：应用登记即自动开通 OIDC 客户端；编码不可改；Keycloak 失败不置 ACTIVE。 */
class AppRegistrarTest {

    private static final String CALLBACK = "https://order.example.com/login/callback";

    private final InMemoryAppRepository apps = new InMemoryAppRepository();
    private final InMemoryAppClientRepository clients = new InMemoryAppClientRepository();
    private final RecordingKeycloak keycloak = new RecordingKeycloak();

    private AppRegistrar registrar() {
        return new AppRegistrar(apps, clients, keycloak);
    }

    private static AppRegistrationCmd cmd(String code, String kind) {
        return new AppRegistrationCmd(code, "订单中心", "order-icon", "https://order.example.com",
                "u1", kind, List.of(CALLBACK), "alice");
    }

    @Test
    void registerCreatesAppAndActivatesAutoProvisionedClient() {
        String appId = registrar().register(cmd("order-center", AppKind.SUB));

        assertThat(appId).startsWith("app_");
        App app = apps.byId.get(appId);
        assertThat(app.code()).isEqualTo("order-center");
        assertThat(app.status()).isEqualTo(App.STATUS_ENABLED);
        assertThat(app.manifestVersion()).isZero();

        AppClient client = clients.byId.values().iterator().next();
        assertThat(client.id()).startsWith("cli_");
        assertThat(client.appId()).isEqualTo(appId);
        assertThat(client.clientId()).isEqualTo("order-center");
        assertThat(client.status()).isEqualTo(AppClient.STATUS_ACTIVE);
        assertThat(client.redirectUris()).containsExactly(CALLBACK);
        assertThat(client.grantTypes())
                .containsExactly(OidcGrantTypes.AUTHORIZATION_CODE, OidcGrantTypes.REFRESH_TOKEN);

        assertThat(keycloak.upserts).hasSize(1);
        assertThat(keycloak.upserts.get(0).clientId()).isEqualTo("order-center");
        assertThat(keycloak.upserts.get(0).redirectUris()).containsExactly(CALLBACK);
    }

    @Test
    void registerDefaultsKindToSubsystem() {
        String appId = registrar().register(cmd("order-center", null));

        assertThat(apps.byId.get(appId).kind()).isEqualTo(AppKind.SUB);
    }

    @Test
    void registerRejectsDuplicateCode() {
        registrar().register(cmd("order-center", AppKind.SUB));

        assertThatThrownBy(() -> registrar().register(cmd("order-center", AppKind.SUB)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        assertThat(apps.byId).hasSize(1);
        assertThat(keycloak.upserts).hasSize(1);
    }

    @Test
    void registerRejectsInvalidCodeBeforePersisting() {
        assertThatThrownBy(() -> registrar().register(cmd("Order_Center", AppKind.SUB)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(apps.byId).isEmpty();
        assertThat(keycloak.upserts).isEmpty();
    }

    @Test
    void registerLeavesClientRotatingWhenKeycloakFails() {
        RuntimeException failure = new IllegalStateException("Keycloak 不可用");
        keycloak.failOnUpsert = failure;

        assertThatThrownBy(() -> registrar().register(cmd("order-center", AppKind.SUB))).isSameAs(failure);

        // 本地不把客户端置 ACTIVE（落库行的回滚由集成用例 registerRollbackOnKeycloakFailure 断言）
        assertThat(clients.byId.values()).hasSize(1);
        assertThat(clients.byId.values().iterator().next().status()).isEqualTo(AppClient.STATUS_ROTATING);
    }

    @Test
    void updateChangesProfileAndKeepsCode() {
        String appId = registrar().register(cmd("order-center", AppKind.SUB));

        registrar().update(new AppUpdateCmd("order-center", "订单中心（新）", "new-icon",
                "https://order2.example.com", "u2", App.STATUS_DISABLED, "bob"));

        App updated = apps.byId.get(appId);
        assertThat(updated.code()).isEqualTo("order-center");
        assertThat(updated.name()).isEqualTo("订单中心（新）");
        assertThat(updated.ownerUserId()).isEqualTo("u2");
        assertThat(updated.status()).isEqualTo(App.STATUS_DISABLED);
    }

    @Test
    void updateRejectsUnknownApp() {
        assertThatThrownBy(() -> registrar().update(new AppUpdateCmd("missing", "名称", null, null, "u1",
                App.STATUS_ENABLED, "bob")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void updateRejectsUnknownStatus() {
        registrar().register(cmd("order-center", AppKind.SUB));

        assertThatThrownBy(() -> registrar().update(new AppUpdateCmd("order-center", "订单中心", null, null,
                "u1", "ARCHIVED", "bob")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void listAndFindReturnViews() {
        registrar().register(cmd("order-center", AppKind.SUB));

        assertThat(registrar().list()).extracting(AppView::code).containsExactly("order-center");
        Optional<AppView> found = registrar().find("order-center");
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("订单中心");
        assertThat(registrar().find("missing")).isEmpty();
    }
}
