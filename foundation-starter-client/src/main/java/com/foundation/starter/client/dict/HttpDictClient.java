package com.foundation.starter.client.dict;

import com.fasterxml.jackson.databind.JsonNode;
import com.foundation.starter.client.cache.TtlCache;
import com.foundation.starter.client.http.FoundationHttpClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 字典读取的 HTTP 实现（F-07 §7）：{@code GET /api/foundation/dict/{type}}，带本地缓存。
 *
 * <p>全局项（{@code appCode} 为空）与本应用项合并，**应用项优先**（F-02 §BAS-09）。
 */
public class HttpDictClient implements DictClient {

    private final FoundationHttpClient http;
    private final TtlCache<String, List<DictItem>> cache;
    private final String appCode;

    public HttpDictClient(FoundationHttpClient http, TtlCache<String, List<DictItem>> cache, String appCode) {
        this.http = http;
        this.cache = cache;
        this.appCode = appCode;
    }

    @Override
    public List<DictItem> items(String typeCode) {
        List<DictItem> items = cache.getWithDegrade("fdn:dict:" + typeCode, () -> fetch(typeCode));
        return items == null ? List.of() : items;
    }

    private List<DictItem> fetch(String typeCode) {
        JsonNode node = http.get("/api/foundation/dict/" + typeCode);
        if (node == null) {
            return List.of();
        }
        JsonNode array = node.isArray() ? node : node.path("items");
        List<DictItem> items = new ArrayList<>();
        array.forEach(item -> items.add(new DictItem(
                typeCode,
                item.path("itemCode").asText(null),
                item.path("itemName").asText(null),
                item.path("sort").asInt(0),
                item.path("appCode").asText(null))));
        // 应用项优先（稳定排序：本应用项排在全局项之前）
        items.sort(Comparator.comparing(item -> appCode != null && appCode.equals(item.appCode()) ? 0 : 1));
        return items;
    }
}
