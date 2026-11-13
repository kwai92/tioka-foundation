package com.foundation.app.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.api.keycloak.OidcGrantTypes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-05 BAS-04：回调地址与 grant 组合校验（文档用例 {@code ClientRegistrationCommandTest}）。 */
class ClientRegistrationTest {

    private static final String CALLBACK = "https://order.example.com/login/callback";

    @Test
    void defaultsToAuthorizationCodeAndRefreshToken() {
        ClientRegistration registration = ClientRegistration.of(null, List.of(CALLBACK));

        assertThat(registration.grantTypes())
                .containsExactly(OidcGrantTypes.AUTHORIZATION_CODE, OidcGrantTypes.REFRESH_TOKEN);
        assertThat(registration.redirectUris()).containsExactly(CALLBACK);
    }

    @Test
    void keepsGrantOrderAndRemovesDuplicates() {
        ClientRegistration registration = ClientRegistration.of(
                List.of(OidcGrantTypes.REFRESH_TOKEN, OidcGrantTypes.AUTHORIZATION_CODE,
                        OidcGrantTypes.REFRESH_TOKEN),
                List.of(CALLBACK, CALLBACK));

        assertThat(registration.grantTypes())
                .containsExactly(OidcGrantTypes.REFRESH_TOKEN, OidcGrantTypes.AUTHORIZATION_CODE);
        assertThat(registration.redirectUris()).containsExactly(CALLBACK);
    }

    @Test
    void rejectsUnsupportedGrantType() {
        assertThatThrownBy(() -> ClientRegistration.of(List.of("device_code"), List.of()))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void authorizationCodeRequiresRedirectUri() {
        assertThatThrownBy(() -> ClientRegistration.of(List.of(OidcGrantTypes.AUTHORIZATION_CODE), List.of()))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void implicitRequiresRedirectUri() {
        assertThatThrownBy(() -> ClientRegistration.of(List.of(OidcGrantTypes.IMPLICIT), null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void clientCredentialsMustNotMixWithOtherGrants() {
        assertThatThrownBy(() -> ClientRegistration.of(
                List.of(OidcGrantTypes.CLIENT_CREDENTIALS, OidcGrantTypes.AUTHORIZATION_CODE), List.of(CALLBACK)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void rejectsRelativeOrNonHttpRedirectUri() {
        assertThatThrownBy(() -> ClientRegistration.of(List.of(OidcGrantTypes.AUTHORIZATION_CODE), List.of("/cb")))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> ClientRegistration.of(List.of(OidcGrantTypes.AUTHORIZATION_CODE),
                List.of("ftp://order.example.com/cb")))
                .isInstanceOf(BizException.class);
    }

    @Test
    void serviceAccountUsesClientCredentialsOnly() {
        ClientRegistration registration = ClientRegistration.serviceAccount();

        assertThat(registration.grantTypes()).containsExactly(OidcGrantTypes.CLIENT_CREDENTIALS);
        assertThat(registration.redirectUris()).isEmpty();
    }

    @Test
    void clientCredentialsAloneNeedsNoRedirect() {
        ClientRegistration registration =
                ClientRegistration.of(List.of(OidcGrantTypes.CLIENT_CREDENTIALS), List.of());

        assertThat(registration.grantTypes()).containsExactly(OidcGrantTypes.CLIENT_CREDENTIALS);
    }
}
