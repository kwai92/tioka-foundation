package com.foundation.app.application;

import com.foundation.app.api.ClientView;
import com.foundation.app.application.TestDoubles.InMemoryAppClientRepository;
import com.foundation.app.application.TestDoubles.InMemoryAppRepository;
import com.foundation.app.application.TestDoubles.RecordingKeycloak;
import com.foundation.app.domain.App;
import com.foundation.app.domain.AppClient;
import com.foundation.app.domain.AppKind;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-05 BAS-04：密钥轮换由 Keycloak 生成新密钥，本地只更新 secret_rotated_at（明文不落库）。 */
class ClientSecretRotatorTest {

    private final InMemoryAppRepository apps = new InMemoryAppRepository();
    private final InMemoryAppClientRepository clients = new InMemoryAppClientRepository();
    private final RecordingKeycloak keycloak = new RecordingKeycloak();

    private ClientSecretRotator rotator() {
        return new ClientSecretRotator(apps, clients, keycloak);
    }

    @BeforeEach
    void seedAppAndClient() {
        App app = App.register("app_1", "order-center", "订单中心", null, null, AppKind.SUB, "u1");
        apps.insert(app, "alice");
        clients.insert(new AppClient("cli_1", "app_1", "order-center", List.of("https://order.example.com/cb"),
                List.of("authorization_code", "refresh_token"), AppClient.STATUS_ACTIVE, null), "alice");
    }

    @Test
    void rotateSecretReturnsNewSecretAndStampsRotatedAt() {
        String secret = rotator().rotateSecret("order-center", "order-center", "bob");

        assertThat(secret).isEqualTo("generated-secret");
        assertThat(keycloak.regenerated).containsExactly("order-center");
        AppClient client = clients.byId.get("cli_1");
        assertThat(client.secretRotatedAt()).isNotNull();
        assertThat(client.status()).isEqualTo(AppClient.STATUS_ACTIVE);
    }

    @Test
    void rotateSecretRejectsUnknownApp() {
        assertThatThrownBy(() -> rotator().rotateSecret("missing", "missing", "bob"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void rotateSecretRejectsUnknownClientOfKnownApp() {
        assertThatThrownBy(() -> rotator().rotateSecret("order-center", "other-client", "bob"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(keycloak.regenerated).isEmpty();
    }

    @Test
    void listByAppReturnsClientViews() {
        List<ClientView> views = rotator().listByApp("order-center");

        assertThat(views).hasSize(1);
        assertThat(views.get(0).clientId()).isEqualTo("order-center");
        assertThat(views.get(0).appId()).isEqualTo("app_1");
        assertThat(views.get(0).grantTypes()).containsExactly("authorization_code", "refresh_token");
        assertThat(views.get(0).secretRotatedAt()).isNull();
    }

    @Test
    void listByAppRejectsUnknownApp() {
        assertThatThrownBy(() -> rotator().listByApp("missing"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void rotatedAtIsInThePast() {
        rotator().rotateSecret("order-center", "order-center", null);

        Instant rotatedAt = clients.byId.get("cli_1").secretRotatedAt();
        assertThat(rotatedAt).isBeforeOrEqualTo(Instant.now());
    }
}
