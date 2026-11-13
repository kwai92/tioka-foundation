package com.foundation.starter.client.org;

import com.fasterxml.jackson.databind.JsonNode;
import com.foundation.starter.client.cache.TtlCache;
import com.foundation.starter.client.http.FoundationHttpClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 人员查询的 HTTP 实现（F-07 §8）：按工号 / ID / 组织查人员，带本地缓存。
 */
public class HttpPersonClient implements PersonClient {

    private final FoundationHttpClient http;
    private final TtlCache<String, Person> personCache;
    private final TtlCache<String, List<Person>> listCache;

    public HttpPersonClient(FoundationHttpClient http,
                            TtlCache<String, Person> personCache,
                            TtlCache<String, List<Person>> listCache) {
        this.http = http;
        this.personCache = personCache;
        this.listCache = listCache;
    }

    @Override
    public Optional<Person> byEmployeeNo(String employeeNo) {
        Person person = personCache.get("fdn:person:no:" + employeeNo,
                () -> toPerson(http.get("/api/foundation/persons?employeeNo=" + encode(employeeNo))));
        return Optional.ofNullable(person);
    }

    @Override
    public Optional<Person> byId(String personId) {
        Person person = personCache.get("fdn:person:id:" + personId,
                () -> toPerson(http.get("/api/foundation/persons/" + personId)));
        return Optional.ofNullable(person);
    }

    @Override
    public List<Person> byOrgUnit(String orgCode, boolean includeSub) {
        List<Person> persons = listCache.get("fdn:person:org:" + orgCode + ":" + includeSub, () -> {
            JsonNode node = http.get("/api/foundation/persons?orgUnit=" + encode(orgCode)
                    + "&includeSub=" + includeSub);
            if (node == null) {
                return List.of();
            }
            JsonNode array = node.isArray() ? node : node.path("items");
            List<Person> list = new ArrayList<>();
            array.forEach(item -> list.add(personOf(item)));
            return list;
        });
        return persons == null ? List.of() : persons;
    }

    private static Person toPerson(JsonNode node) {
        return node == null || node.isNull() ? null : personOf(node);
    }

    private static Person personOf(JsonNode node) {
        return new Person(
                node.path("id").asText(null),
                node.path("employeeNo").asText(null),
                node.path("name").asText(null),
                node.path("accountId").asText(null),
                node.path("primaryOrgUnit").asText(null),
                node.path("status").asText(null));
    }

    private static String encode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
