package com.foundation.starter.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.client.cache.TtlCache;
import com.foundation.starter.client.dict.DictItem;
import com.foundation.starter.client.dict.HttpDictClient;
import com.foundation.starter.client.http.CircuitBreaker;
import com.foundation.starter.client.http.FoundationHttpClient;
import com.foundation.starter.client.param.HttpParamClient;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 字典与参数客户端（F-07 §7）：应用项优先、本地缓存、敏感参数拒读。 */
class DictParamClientTest {

    private MockWebServer server;
    private FoundationHttpClient http;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        http = new FoundationHttpClient(new OkHttpClient(), new ObjectMapper(), baseUrl(), null,
                new CircuitBreaker(50, 20, 60_000, 30_000), 1, 1);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void dictPrefersAppItemsAndServesFromCache() throws Exception {
        server.enqueue(json("""
                [{"itemCode":"global","itemName":"全局项","sort":1},
                 {"itemCode":"local","itemName":"本应用项","sort":2,"appCode":"tioka"}]
                """));
        HttpDictClient client = new HttpDictClient(http,
                new TtlCache<String, List<DictItem>>(60_000), "tioka");

        List<DictItem> first = client.items("gender");
        List<DictItem> second = client.items("gender");

        assertThat(first).extracting(DictItem::itemCode).containsExactly("local", "global");
        assertThat(second).isSameAs(first);
        assertThat(server.getRequestCount()).isEqualTo(1);
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/foundation/dict/gender");
    }

    @Test
    void paramReturnsReadableValue() {
        server.enqueue(json("{\"value\":\"v1\"}"));
        HttpParamClient client = new HttpParamClient(http, new TtlCache<>(60_000));

        assertThat(client.value("app.name")).contains("v1");
    }

    @Test
    void secretParamIsNotReadableButStillCached() {
        server.enqueue(json("{\"value\":\"s3cr3t\",\"secret\":true}"));
        HttpParamClient client = new HttpParamClient(http, new TtlCache<>(60_000));

        assertThat(client.value("db.password")).isEmpty();
        assertThat(client.value("db.password")).isEmpty();

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    private String baseUrl() {
        String url = server.url("/").toString();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static MockResponse json(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
    }
}
