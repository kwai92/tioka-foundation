package com.foundation.starter.mq.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.mq.config.FoundationMqProperties;
import com.foundation.starter.mq.outbox.OutboxEntry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ADP-02：Webhook 通道的 envelope、HMAC 签名、白名单与失败重试语义。 */
class WebhookEventSinkTest {

    private static final String SECRET = "it-secret";

    private HttpServer server;
    private final LinkedBlockingQueue<Recorded> received = new LinkedBlockingQueue<>();
    private final AtomicInteger responseCode = new AtomicInteger(200);
    private FoundationMqProperties.Webhook properties;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            received.add(new Recorded(body, exchange.getRequestHeaders()));
            exchange.sendResponseHeaders(responseCode.get(), -1);
            exchange.close();
        });
        server.start();
        properties = new FoundationMqProperties.Webhook();
        properties.setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/hook");
        properties.setSecret(SECRET);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void 投递envelope并带正确签名() throws Exception {
        new WebhookEventSink(properties, new ObjectMapper()).deliver(entry());

        Recorded recorded = received.poll(5, TimeUnit.SECONDS);
        assertTrue(recorded != null, "Webhook 应收到一次 POST");
        JsonNode body = new ObjectMapper().readTree(recorded.body());
        assertEquals(1, body.path("schemaVersion").asInt());
        assertEquals("fdn.dict.changed", body.path("eventType").asText());
        assertEquals("evt_1", body.path("deliveryId").asText());
        assertEquals("dt_1", body.path("payload").path("id").asText());
        assertTrue(!body.path("payload").has("schemaVersion"), "保留字段应只出现在 envelope 顶层");

        assertEquals("fdn.dict.changed", recorded.headers().getFirst("X-Fdn-Event"));
        assertEquals("1", recorded.headers().getFirst("X-Fdn-Schema-Version"));
        assertEquals("evt_1", recorded.headers().getFirst("X-Fdn-Delivery-Id"));
        assertEquals("sha256=" + expectedSignature(recorded.body()),
                recorded.headers().getFirst("X-Fdn-Signature"));
    }

    @Test
    void 非2xx抛异常以触发重试() {
        responseCode.set(503);
        WebhookEventSink sink = new WebhookEventSink(properties, new ObjectMapper());

        assertThrows(IllegalStateException.class, () -> sink.deliver(entry()));
    }

    @Test
    void 白名单外事件不投递() throws Exception {
        properties.setAllowedEventTypes(List.of("fdn.authz.changed"));
        new WebhookEventSink(properties, new ObjectMapper()).deliver(entry());

        assertTrue(received.poll(1, TimeUnit.SECONDS) == null, "白名单外事件不应投递");
    }

    @Test
    void 缺少签名密钥时启动失败() {
        properties.setSecret(null);
        assertThrows(IllegalStateException.class,
                () -> new WebhookEventSink(properties, new ObjectMapper()));
    }

    private OutboxEntry entry() {
        return new OutboxEntry("evt_1", "fdn-events", "fdn.dict.changed", "dt_1",
                "{\"id\":\"dt_1\",\"schemaVersion\":1}", OutboxEntry.STATUS_NEW, 0,
                LocalDateTime.now(), null);
    }

    private String expectedSignature(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }

    private record Recorded(byte[] body, com.sun.net.httpserver.Headers headers) {
    }
}
