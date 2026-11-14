package com.foundation.sync.scim.api;

import com.foundation.starter.security.permission.PublicApi;
import com.foundation.sync.scim.application.ScimRepresentation;
import com.foundation.sync.scim.application.ScimTokenService;
import com.foundation.sync.scim.domain.ScimConstants;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCIM 2.0 服务发现（F-08 BAS-13 §1，RFC 7643 §4 / RFC 7644 §4）：
 * {@code /scim/v2/ServiceProviderConfig}、{@code /ResourceTypes}、{@code /Schemas}。
 *
 * <p>能力声明：仅支持 {@code PATCH} 与单条件 {@code filter}（{@code eq}/{@code co}/{@code sw}）；
 * {@code Bulk} 声明为**不支持**（{@code bulk.supported=false}）。认证同资源端点（Bearer 令牌）。
 */
@ResponseBody
@RequestMapping("/scim/v2")
public class ScimDiscoveryController {

    /** ResourceType 的 schema。 */
    private static final String RESOURCE_TYPE_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:ResourceType";

    /** Schema 的 schema。 */
    private static final String SCHEMA_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Schema";

    /** ServiceProviderConfig 的 schema。 */
    private static final String CONFIG_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig";

    /** 单次查询返回的最大结果数。 */
    private static final int MAX_RESULTS = 200;

    private final ScimTokenService tokens;

    public ScimDiscoveryController(ScimTokenService tokens) {
        this.tokens = tokens;
    }

    @GetMapping("/ServiceProviderConfig")
    @PublicApi
    public Map<String, Object> serviceProviderConfig(
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        tokens.authenticate(authorization);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemas", List.of(CONFIG_SCHEMA));
        body.put("documentationUri", "https://tioka.example.com/docs/scim");
        body.put("patch", Map.of("supported", true));
        body.put("bulk", Map.of("supported", false, "maxOperations", 0, "maxPayloadSize", 0));
        body.put("filter", Map.of("supported", true, "maxResults", MAX_RESULTS));
        body.put("changePassword", Map.of("supported", false));
        body.put("sort", Map.of("supported", true));
        body.put("etag", Map.of("supported", false));
        body.put("authenticationSchemes", List.of(Map.of(
                "type", "oauthbearertoken",
                "name", "OAuth Bearer Token",
                "description", "Authorization: Bearer <token>",
                "primary", true)));
        return body;
    }

    @GetMapping("/ResourceTypes")
    @PublicApi
    public Map<String, Object> resourceTypes(
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        tokens.authenticate(authorization);
        List<Map<String, Object>> resources = List.of(
                resourceType("User", ScimConstants.USERS_PATH, ScimRepresentation.USER_SCHEMA),
                resourceType("Group", ScimConstants.GROUPS_PATH, ScimRepresentation.GROUP_SCHEMA));
        return ScimRepresentation.list(resources, 1, resources.size());
    }

    @GetMapping("/Schemas")
    @PublicApi
    public Map<String, Object> schemas(
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        tokens.authenticate(authorization);
        List<Map<String, Object>> resources = List.of(
                schema(ScimRepresentation.USER_SCHEMA, "User", List.of("userName", "externalId", "displayName",
                        "active", "meta")),
                schema(ScimRepresentation.GROUP_SCHEMA, "Group", List.of("displayName", "externalId", "meta")));
        return ScimRepresentation.list(resources, 1, resources.size());
    }

    private static Map<String, Object> resourceType(String name, String endpoint, String schema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemas", List.of(RESOURCE_TYPE_SCHEMA));
        body.put("id", name);
        body.put("name", name);
        body.put("endpoint", endpoint);
        body.put("schema", schema);
        return body;
    }

    private static Map<String, Object> schema(String id, String name, List<String> attributeNames) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemas", List.of(SCHEMA_SCHEMA));
        body.put("id", id);
        body.put("name", name);
        body.put("attributes", attributeNames.stream()
                .map(attribute -> Map.<String, Object>of("name", attribute, "type", "string", "multiValued", false))
                .toList());
        return body;
    }
}
