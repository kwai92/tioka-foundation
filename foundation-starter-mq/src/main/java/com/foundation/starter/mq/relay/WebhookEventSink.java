package com.foundation.starter.mq.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foundation.starter.mq.config.FoundationMqProperties;
import com.foundation.starter.mq.event.EventSchemas;
import com.foundation.starter.mq.outbox.OutboxEntry;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Set;

/**
 * Webhook 事件通道（ADP-02 / S15 §12.4、§14.2）。
 *
 * <p>让非 Java / 不连 RocketMQ 的接入方也能订阅 {@code fdn.*} 事件：POST 一个带契约版本的
 * envelope，用 {@code X-Fdn-Signature: sha256=<hmac>} 签名，非 2xx 抛异常以触发 outbox 重试。
 *
 * <p>MQ 通道的 body 保持原样（只新增 payload 内保留字段 {@code schemaVersion}，属兼容变更）；
 * Webhook 通道用 envelope 携带元数据，两种形态在契约文件
 * {@code /contracts/event-schemas-v1.json} 中分别声明。
 */
public class WebhookEventSink implements EventSink {

    private static final String SIGNATURE_HEADER = "X-Fdn-Signature";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final FoundationMqProperties.Webhook properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Set<String> allowedEventTypes;

    public WebhookEventSink(FoundationMqProperties.Webhook properties, ObjectMapper objectMapper) {
        if (properties == null || properties.getUrl() == null || properties.getUrl().isBlank()) {
            throw new IllegalStateException("启用 foundation.mq.webhook 时必须配置 url");
        }
        if (properties.getSecret() == null || properties.getSecret().isBlank()) {
            throw new IllegalStateException("启用 foundation.mq.webhook 时必须配置 secret（HMAC 签名密钥）");
        }
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.allowedEventTypes = properties.getAllowedEventTypes() == null
                ? Set.of() : Set.copyOf(properties.getAllowedEventTypes());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
    }

    @Override
    public String name() {
        return "webhook";
    }

    @Override
    public void deliver(OutboxEntry entry) {
        if (!allowedEventTypes.isEmpty() && !allowedEventTypes.contains(entry.eventType())) {
            return;
        }
        Envelope envelope = envelope(entry);
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getUrl()))
                .timeout(properties.getTimeout())
                .header("Content-Type", "application/json")
                .header("X-Fdn-Event", entry.eventType())
                .header("X-Fdn-Schema-Version", String.valueOf(envelope.schemaVersion()))
                .header("X-Fdn-Delivery-Id", entry.id())
                .header(SIGNATURE_HEADER, "sha256=" + sign(envelope.body()))
                .POST(HttpRequest.BodyPublishers.ofByteArray(envelope.body()))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Webhook 投递失败，HTTP " + response.statusCode());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Webhook 投递失败：" + entry.eventType(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Webhook 投递被中断：" + entry.eventType(), ex);
        }
    }

    /** 构造 envelope：把 payload 内的保留字段 schemaVersion 提升到顶层，避免重复。 */
    private Envelope envelope(OutboxEntry entry) {
        try {
            String raw = entry.payload() == null || entry.payload().isBlank() ? "{}" : entry.payload();
            JsonNode payload = objectMapper.readTree(raw);
            int schemaVersion = EventSchemas.versionOf(entry.eventType());
            if (payload.isObject() && payload.has("schemaVersion")) {
                schemaVersion = payload.get("schemaVersion").asInt(schemaVersion);
                ((ObjectNode) payload).remove("schemaVersion");
            }
            ObjectNode body = objectMapper.createObjectNode();
            body.put("schemaVersion", schemaVersion);
            body.put("eventType", entry.eventType());
            body.put("aggregate", entry.aggregate());
            body.put("deliveryId", entry.id());
            body.set("payload", payload);
            return new Envelope(objectMapper.writeValueAsBytes(body), schemaVersion);
        } catch (IOException ex) {
            throw new IllegalStateException("Webhook envelope 构造失败：" + entry.eventType(), ex);
        }
    }

    private String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC 签名失败", ex);
        }
    }

    private record Envelope(byte[] body, int schemaVersion) {
    }
}
