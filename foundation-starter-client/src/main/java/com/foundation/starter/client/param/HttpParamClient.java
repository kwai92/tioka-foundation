package com.foundation.starter.client.param;

import com.fasterxml.jackson.databind.JsonNode;
import com.foundation.starter.client.cache.TtlCache;
import com.foundation.starter.client.http.FoundationHttpClient;

import java.util.Optional;

/**
 * 参数读取的 HTTP 实现（F-07 §7）：{@code GET /api/foundation/param/{key}}，带本地缓存。
 *
 * <p>**敏感参数只写不读**：{@code secret=true} 时不返回值（F-02 §BAS-09）。
 */
public class HttpParamClient implements ParamClient {

    private final FoundationHttpClient http;
    private final TtlCache<String, ParamEntry> cache;

    public HttpParamClient(FoundationHttpClient http, TtlCache<String, ParamEntry> cache) {
        this.http = http;
        this.cache = cache;
    }

    @Override
    public Optional<String> value(String key) {
        ParamEntry entry = cache.get("fdn:param:" + key, () -> fetch(key));
        if (entry == null || !entry.readable() || entry.value() == null) {
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    private ParamEntry fetch(String key) {
        JsonNode node = http.get("/api/foundation/param/" + key);
        if (node == null) {
            return null;
        }
        if (node.path("secret").asBoolean(false)) {
            return new ParamEntry(null, false);
        }
        return new ParamEntry(node.path("value").asText(null), true);
    }

    /**
     * 参数缓存值。
     *
     * @param value    参数明文；敏感参数为 {@code null}
     * @param readable 是否可读（敏感参数为 {@code false}）
     */
    public record ParamEntry(String value, boolean readable) {
    }
}
