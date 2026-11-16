package com.foundation.starter.client.authz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.client.http.CircuitBreaker;
import com.foundation.starter.client.http.FoundationApiVersionMismatchException;
import com.foundation.starter.client.http.FoundationHttpClient;
import com.foundation.starter.core.contract.FoundationApiVersion;
import com.foundation.starter.core.http.BearerTokenSupplier;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F-06 BAS-07：授权快照客户端只交出原始权限项，结构异常一律按不可用处理。 */
class HttpAuthorizationSnapshotClientTest {

    private static final String APP = "tioka";
    private static final String PERSON = "01JABC0000000000000000000";

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void 正常快照返回原始权限项_出站带版本头与服务账号令牌() throws Exception {
        enqueue(snapshot(PERSON, APP, "[\"agent:read\",\"agent:write\"]"));

        Set<String> codes = client().permissionsFor(PERSON);

        assertEquals(Set.of("agent:read", "agent:write"), codes);
        RecordedRequest request = server.takeRequest();
        assertEquals("/api/fdn/apps/tioka/persons/" + PERSON + "/snapshot", request.getPath());
        assertEquals(FoundationApiVersion.CURRENT, request.getHeader(FoundationApiVersion.HEADER));
        assertEquals("Bearer svc-token", request.getHeader("Authorization"));
    }

    @Test
    void 空权限数组表示中台明确授予零个权限项() {
        enqueue(snapshot(PERSON, APP, "[]"));

        Set<String> codes = client().permissionsFor(PERSON);

        assertTrue(codes != null && codes.isEmpty());
    }

    @Test
    void 响应身份与请求不一致时拒绝使用快照() {
        enqueue(snapshot("someone-else", APP, "[\"agent:read\"]"));
        assertNull(client().permissionsFor(PERSON));

        enqueue(snapshot(PERSON, "other-app", "[\"agent:read\"]"));
        assertNull(client().permissionsFor(PERSON));
    }

    @Test
    void 权限项结构非法时整份快照作废() {
        enqueue("{\"personId\":\"" + PERSON + "\",\"appCode\":\"" + APP + "\",\"version\":7}");
        assertNull(client().permissionsFor(PERSON));

        enqueue(snapshot(PERSON, APP, "\"agent:read\""));
        assertNull(client().permissionsFor(PERSON));

        enqueue(snapshot(PERSON, APP, "[\"agent:read\", 7]"));
        assertNull(client().permissionsFor(PERSON));
    }

    @Test
    void 空响应与非二xx按不可用处理() {
        server.enqueue(new MockResponse().setResponseCode(200));
        assertNull(client().permissionsFor(PERSON));

        server.enqueue(new MockResponse().setResponseCode(404).setBody("not found"));
        assertThrows(RuntimeException.class, () -> client().permissionsFor(PERSON));
    }

    @Test
    void 非法JSON按不可用处理() {
        server.enqueue(new MockResponse().setBody("{broken")
                .setHeader("Content-Type", "application/json"));
        assertThrows(RuntimeException.class, () -> client().permissionsFor(PERSON));
    }

    @Test
    void 响应主版本错配不重试() {
        server.enqueue(new MockResponse()
                .setBody(snapshot(PERSON, APP, "[\"agent:read\"]"))
                .setHeader(FoundationApiVersion.HEADER, "2"));

        assertThrows(FoundationApiVersionMismatchException.class,
                () -> client(3, () -> "svc-token").permissionsFor(PERSON));
        assertEquals(1, server.getRequestCount(), "契约错配不是上游抖动，不能重试");
    }

    @Test
    void 人员标识被URL编码_空白标识不发请求() throws Exception {
        String person = "a b/c";
        enqueue(snapshot(person, APP, "[]"));

        assertTrue(client().permissionsFor(person).isEmpty());
        assertEquals("/api/fdn/apps/tioka/persons/a%20b%2Fc/snapshot",
                server.takeRequest().getPath());

        assertNull(client().permissionsFor("  "));
        assertEquals(1, server.getRequestCount(), "空白标识不得产生出站请求");
    }

    @Test
    void 非法应用编码在构造期拒绝() {
        assertThrows(IllegalArgumentException.class,
                () -> new HttpAuthorizationSnapshotClient(foundationHttp(1), "../evil"));
    }

    private AuthorizationSnapshotClient client() {
        return client(1, () -> "svc-token");
    }

    private AuthorizationSnapshotClient client(int attempts, BearerTokenSupplier token) {
        return new HttpAuthorizationSnapshotClient(foundationHttp(attempts, token), APP);
    }

    private FoundationHttpClient foundationHttp(int attempts) {
        return foundationHttp(attempts, () -> "svc-token");
    }

    private FoundationHttpClient foundationHttp(int attempts, BearerTokenSupplier token) {
        return new FoundationHttpClient(new OkHttpClient(), new ObjectMapper(),
                server.url("/").toString().replaceAll("/$", ""), token,
                new CircuitBreaker(50, 20, 60_000, 30_000), attempts, 1);
    }

    private void enqueue(String body) {
        server.enqueue(new MockResponse().setBody(body)
                .setHeader("Content-Type", "application/json"));
    }

    private static String snapshot(String personId, String appCode, String permissions) {
        return "{\"personId\":\"" + personId + "\",\"appCode\":\"" + appCode + "\","
                + "\"version\":7,\"permissions\":" + permissions + "}";
    }
}
