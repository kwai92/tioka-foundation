package com.foundation.starter.client.org;

import com.fasterxml.jackson.databind.JsonNode;
import com.foundation.starter.client.cache.TtlCache;
import com.foundation.starter.client.http.FoundationHttpClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 组织查询的 HTTP 实现（F-07 §8）：按编码解析组织单元与下级，带本地缓存。
 */
public class HttpOrgClient implements OrgClient {

    private final FoundationHttpClient http;
    private final TtlCache<String, OrgUnit> unitCache;
    private final TtlCache<String, List<OrgUnit>> childrenCache;

    public HttpOrgClient(FoundationHttpClient http,
                         TtlCache<String, OrgUnit> unitCache,
                         TtlCache<String, List<OrgUnit>> childrenCache) {
        this.http = http;
        this.unitCache = unitCache;
        this.childrenCache = childrenCache;
    }

    @Override
    public Optional<OrgUnit> byCode(String orgCode) {
        OrgUnit unit = unitCache.getWithDegrade("fdn:org:" + orgCode, () -> toUnit(http.get("/api/foundation/org/units/" + orgCode)));
        return Optional.ofNullable(unit);
    }

    @Override
    public List<OrgUnit> children(String orgCode) {
        List<OrgUnit> units = childrenCache.getWithDegrade("fdn:org:" + orgCode + ":children", () -> {
            JsonNode node = http.get("/api/foundation/org/units/" + orgCode + "/children");
            if (node == null) {
                return List.of();
            }
            JsonNode array = node.isArray() ? node : node.path("items");
            List<OrgUnit> list = new ArrayList<>();
            array.forEach(item -> list.add(unitOf(item)));
            return list;
        });
        return units == null ? List.of() : units;
    }

    private static OrgUnit toUnit(JsonNode node) {
        return node == null || node.isNull() ? null : unitOf(node);
    }

    private static OrgUnit unitOf(JsonNode node) {
        return new OrgUnit(
                node.path("id").asText(null),
                node.path("code").asText(null),
                node.path("name").asText(null),
                node.path("parentId").asText(null),
                node.path("sort").asInt(0));
    }
}
