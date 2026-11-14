package com.foundation.starter.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.client.http.CircuitBreaker;
import com.foundation.starter.client.http.FoundationHttpClient;
import com.foundation.starter.client.sequence.HttpSequenceClient;
import com.foundation.starter.client.sequence.SequenceCurrent;
import com.foundation.starter.client.sequence.SequenceValues;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 编号取号客户端（F-09 §18.2）：JSON POST 体、幂等键透传、当前值查询。 */
class SequenceClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockWebServer server;
    private HttpSequenceClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        FoundationHttpClient http = new FoundationHttpClient(new OkHttpClient(), objectMapper,
                baseUrl(), null, new CircuitBreaker(50, 20, 60_000, 30_000), 1, 1);
        client = new HttpSequenceClient(http);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void nextPostsRuleCodeAndCountWithIdempotencyKey() throws Exception {
        server.enqueue(json("{\"values\":[\"HT202602130001\",\"HT202602130002\"]}"));

        SequenceValues values = client.next("CONTRACT", 2, "key-1");

        assertThat(values.values()).containsExactly("HT202602130001", "HT202602130002");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/fdn/sequences:next");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeader("Idempotency-Key")).isEqualTo("key-1");
        JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
        assertThat(body.path("ruleCode").asText()).isEqualTo("CONTRACT");
        assertThat(body.path("count").asInt()).isEqualTo(2);
    }

    @Test
    void nextWithoutIdempotencyKeyOmitsHeader() throws Exception {
        server.enqueue(json("{\"values\":[\"HT202602130001\"]}"));

        client.next("CONTRACT", 1, null);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("Idempotency-Key")).isNull();
        assertThat(objectMapper.readTree(request.getBody().readUtf8()).path("count").asInt()).isEqualTo(1);
    }

    @Test
    void currentReadsPeriodValue() throws Exception {
        server.enqueue(json("{\"ruleCode\":\"CONTRACT\",\"periodKey\":\"20260213\",\"current\":7}"));

        SequenceCurrent current = client.current("CONTRACT");

        assertThat(current.ruleCode()).isEqualTo("CONTRACT");
        assertThat(current.periodKey()).isEqualTo("20260213");
        assertThat(current.current()).isEqualTo(7L);
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/fdn/sequences/CONTRACT/current");
    }

    @Test
    void emptyResponseYieldsEmptyValues() {
        server.enqueue(new MockResponse().setResponseCode(204));

        SequenceValues values = client.next("CONTRACT", 1, null);

        assertThat(values.values()).isEmpty();
    }

    /** 验证 {@code postJson} 可序列化普通 Map（幂等键之外的通用能力）。 */
    @Test
    void postJsonIsReusableThroughHttpClient() throws Exception {
        server.enqueue(json("{\"ok\":true}"));
        FoundationHttpClient http = new FoundationHttpClient(new OkHttpClient(), objectMapper,
                baseUrl(), null, new CircuitBreaker(50, 20, 60_000, 30_000), 1, 1);

        JsonNode node = http.postJson("/ping", Map.of("a", 1), Map.of());

        assertThat(node.path("ok").asBoolean()).isTrue();
        assertThat(server.takeRequest().getPath()).isEqualTo("/ping");
    }

    private String baseUrl() {
        String url = server.url("/").toString();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static MockResponse json(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
    }
}
