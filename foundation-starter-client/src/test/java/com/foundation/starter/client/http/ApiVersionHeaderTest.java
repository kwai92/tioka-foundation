package com.foundation.starter.client.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.core.contract.FoundationApiVersion;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** ADP-03：starter 客户端的每次出站请求都声明主版本，服务端据此协商。 */
class ApiVersionHeaderTest {

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

    private FoundationHttpClient http() {
        return new FoundationHttpClient(new OkHttpClient(), new ObjectMapper(),
                server.url("/").toString().replaceAll("/$", ""), null,
                new CircuitBreaker(50, 20, 60_000, 30_000), 1, 1);
    }

    @Test
    void get请求携带版本头() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"ok\":true}")
                .setHeader("Content-Type", "application/json"));

        http().get("/api/foundation/dict/gender");

        assertEquals(FoundationApiVersion.CURRENT,
                server.takeRequest().getHeader(FoundationApiVersion.HEADER));
    }

    @Test
    void post与delete请求同样携带版本头() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"ok\":true}")
                .setHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse().setResponseCode(204));

        http().postJson("/api/fdn/sequences:next", Map.of("ruleCode", "X"), null);
        http().delete("/api/fdn/files/f1");

        assertEquals(FoundationApiVersion.CURRENT,
                server.takeRequest().getHeader(FoundationApiVersion.HEADER));
        assertEquals(FoundationApiVersion.CURRENT,
                server.takeRequest().getHeader(FoundationApiVersion.HEADER));
    }
}
