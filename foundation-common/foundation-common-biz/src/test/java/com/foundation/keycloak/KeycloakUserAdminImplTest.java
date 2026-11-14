package com.foundation.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.common.api.keycloak.KeycloakRequiredActions;
import com.foundation.common.api.keycloak.KeycloakSessionView;
import com.foundation.common.api.keycloak.KeycloakUserSpec;
import com.foundation.common.api.keycloak.KeycloakUserView;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link KeycloakUserAdminImpl} 的请求映射与响应解析（F-04 BAS-03 §5、§6）。 */
class KeycloakUserAdminImplTest {

    private MockWebServer server;
    private ObjectMapper json;
    private KeycloakUserAdminImpl admin;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        json = new ObjectMapper();
        KeycloakProperties properties = new KeycloakProperties();
        properties.setServerUrl(server.url("/").toString());
        properties.setRealm("enterprise");
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(2));
        admin = new KeycloakUserAdminImpl(properties, new StubAdminClient("test-token"), json);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void createUserPostsRepresentationAndReadsLocation() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));
        server.enqueue(new MockResponse().setResponseCode(201)
                .addHeader("Location", server.url("/admin/realms/enterprise/users/kc-1").toString()));

        String id = admin.createUser(new KeycloakUserSpec("e1001", "e1001@corp.com", "张三", null, true,
                List.of(KeycloakRequiredActions.UPDATE_PASSWORD)));

        assertThat(id).isEqualTo("kc-1");
        server.takeRequest();
        RecordedRequest post = server.takeRequest();
        assertThat(post.getMethod()).isEqualTo("POST");
        assertThat(post.getPath()).isEqualTo("/admin/realms/enterprise/users");
        JsonNode body = json.readTree(post.getBody().readUtf8());
        assertThat(body.path("username").asText()).isEqualTo("e1001");
        assertThat(body.path("enabled").asBoolean()).isTrue();
        assertThat(body.path("requiredActions").get(0).asText()).isEqualTo(KeycloakRequiredActions.UPDATE_PASSWORD);
    }

    @Test
    void createUserIsIdempotentByUsername() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "[{\"id\":\"kc-9\",\"username\":\"e1001\",\"enabled\":true}]"));

        String id = admin.createUser(new KeycloakUserSpec("e1001", null, null, null, true, List.of()));

        assertThat(id).isEqualTo("kc-9");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void setEnabledSendsPatch() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        admin.setEnabled("kc-1", false);

        RecordedRequest put = server.takeRequest();
        assertThat(put.getMethod()).isEqualTo("PUT");
        assertThat(put.getPath()).isEqualTo("/admin/realms/enterprise/users/kc-1");
        JsonNode body = json.readTree(put.getBody().readUtf8());
        assertThat(body.path("enabled").asBoolean()).isFalse();
        assertThat(body.has("username")).isFalse();
    }

    @Test
    void findUserReturnsNullOnNotFound() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{}"));

        assertThat(admin.findUser("missing")).isNull();
    }

    @Test
    void listUsersPaginatesUntilShortPage() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(page(100)));
        server.enqueue(new MockResponse().setResponseCode(200).setBody(page(1)));

        List<KeycloakUserView> users = admin.listUsers();

        assertThat(users).hasSize(101);
        assertThat(server.takeRequest().getPath()).isEqualTo("/admin/realms/enterprise/users?first=0&max=100");
        assertThat(server.takeRequest().getPath()).isEqualTo("/admin/realms/enterprise/users?first=100&max=100");
    }

    @Test
    void resetPasswordMarksTemporary() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        admin.resetPassword("kc-1", "Temp#12345", true);

        RecordedRequest put = server.takeRequest();
        assertThat(put.getPath()).isEqualTo("/admin/realms/enterprise/users/kc-1/reset-password");
        JsonNode body = json.readTree(put.getBody().readUtf8());
        assertThat(body.path("type").asText()).isEqualTo("password");
        assertThat(body.path("value").asText()).isEqualTo("Temp#12345");
        assertThat(body.path("temporary").asBoolean()).isTrue();
    }

    @Test
    void logoutSessionsPostsLogout() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));

        admin.logoutSessions("kc-1");

        RecordedRequest post = server.takeRequest();
        assertThat(post.getMethod()).isEqualTo("POST");
        assertThat(post.getPath()).isEqualTo("/admin/realms/enterprise/users/kc-1/logout");
    }

    @Test
    void listSessionsParsesViews() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "[{\"id\":\"s-1\",\"userId\":\"kc-1\",\"username\":\"e1001\",\"ipAddress\":\"10.0.0.1\","
                        + "\"start\":1000,\"lastAccess\":2000}]"));

        List<KeycloakSessionView> sessions = admin.listSessions("kc-1");

        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).id()).isEqualTo("s-1");
        assertThat(sessions.get(0).ipAddress()).isEqualTo("10.0.0.1");
        assertThat(sessions.get(0).lastAccess()).isEqualTo(2000L);
    }

    @Test
    void nonSuccessStatusThrows() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        assertThatThrownBy(() -> admin.setEnabled("kc-1", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 500");
    }

    private static String page(int count) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":\"kc-").append(i).append("\",\"username\":\"u").append(i).append("\",\"enabled\":true}");
        }
        return sb.append(']').toString();
    }

    /** 测试替身：跳过令牌获取，固定返回本地令牌。 */
    private record StubAdminClient(String token) implements KeycloakAdminClient {

        @Override
        public String realm() {
            return "enterprise";
        }

        @Override
        public boolean ping() {
            return true;
        }

        @Override
        public String accessToken() {
            return token;
        }
    }
}
