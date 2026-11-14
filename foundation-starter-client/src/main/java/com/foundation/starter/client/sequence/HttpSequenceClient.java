package com.foundation.starter.client.sequence;

import com.fasterxml.jackson.databind.JsonNode;
import com.foundation.starter.client.http.FoundationHttpClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编号取号的 HTTP 实现（F-09 BAS-18 §18.2 {@code /api/fdn/sequences:next}）。
 *
 * <p>取号是非幂等写接口，由调用方按业务语义提供 {@code Idempotency-Key} 保证重复调用返回同批值。
 */
public class HttpSequenceClient implements SequenceClient {

    private final FoundationHttpClient http;

    public HttpSequenceClient(FoundationHttpClient http) {
        this.http = http;
    }

    @Override
    public SequenceValues next(String ruleCode, int count, String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ruleCode", ruleCode);
        body.put("count", count);
        Map<String, String> headers = idempotencyKey == null || idempotencyKey.isBlank()
                ? Map.of() : Map.of("Idempotency-Key", idempotencyKey);
        JsonNode node = http.postJson("/api/fdn/sequences:next", body, headers);
        if (node == null || node.isNull()) {
            return new SequenceValues(List.of());
        }
        List<String> values = new ArrayList<>();
        node.path("values").forEach(value -> values.add(value.asText(null)));
        return new SequenceValues(values);
    }

    @Override
    public SequenceCurrent current(String ruleCode) {
        JsonNode node = http.get("/api/fdn/sequences/" + encode(ruleCode) + "/current");
        if (node == null || node.isNull()) {
            return null;
        }
        return new SequenceCurrent(
                node.path("ruleCode").asText(null),
                node.path("periodKey").asText(null),
                node.path("current").asLong(0L));
    }

    private static String encode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
