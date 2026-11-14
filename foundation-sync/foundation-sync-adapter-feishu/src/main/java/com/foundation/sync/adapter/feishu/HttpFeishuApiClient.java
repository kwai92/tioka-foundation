package com.foundation.sync.adapter.feishu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.sync.spi.Page;
import com.foundation.sync.spi.PageCursor;
import com.foundation.sync.spi.SyncCredentialsRef;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 飞书通讯录出网实现（F-08 BAS-12）：OkHttp + Jackson。
 *
 * <p>换取 {@code tenant_access_token} 后缓存；命中限流（HTTP 429）或服务端错误（5xx）时按
 * {@code maxRetries}/{@code backoffMs} 指数退避重试；所有网络异常统一包成
 * {@link BizException}({@link ErrorCode#INTERNAL})。不具备删除语义的对象返回空页 /
 * {@link Optional#empty()}。
 */
public class HttpFeishuApiClient implements FeishuApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String DEPARTMENT_ID_TYPE = "open_department_id";
    private static final String USER_ID_TYPE = "open_id";

    private final FeishuProperties properties;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final Object tokenLock = new Object();
    private volatile SyncCredentialsRef credentials;
    private volatile String tenantAccessToken;
    private volatile long tokenExpireAtMillis;

    public HttpFeishuApiClient(FeishuProperties properties) {
        this.properties = properties;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .callTimeout(Duration.ofSeconds(60))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void authenticate(SyncCredentialsRef credentials) {
        this.credentials = credentials;
        // TODO ADR-042: 通过 CredentialCipher / CredentialApi 按 credentials.secretRef() 解析 app_secret，
        //  当前先取配置占位值（properties.appId / properties.appSecret），密钥来源接入后替换此处。
        synchronized (tokenLock) {
            this.tenantAccessToken = null;
            this.tokenExpireAtMillis = 0L;
        }
    }

    @Override
    public boolean ping() {
        try {
            ensureToken();
            HttpUrl url = base().addPathSegments("open-apis/contact/v3/departments")
                    .addQueryParameter("page_size", "1")
                    .addQueryParameter("department_id_type", DEPARTMENT_ID_TYPE)
                    .build();
            JsonNode node = execute(new Request.Builder().url(url).get().build());
            return node.path("code").asInt(-1) == 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public Page<FeishuDepartment> departments(PageCursor cursor) {
        ensureToken();
        HttpUrl.Builder url = base().addPathSegments("open-apis/contact/v3/departments")
                .addQueryParameter("page_size", String.valueOf(properties.getPageSize()))
                .addQueryParameter("department_id_type", DEPARTMENT_ID_TYPE);
        String pageToken = tokenOf(cursor);
        if (pageToken != null) {
            url.addQueryParameter("page_token", pageToken);
        }
        JsonNode node = execute(new Request.Builder().url(url.build()).get().build());
        return parseDepartments(node, cursor);
    }

    @Override
    public Page<FeishuUser> users(PageCursor cursor) {
        ensureToken();
        HttpUrl.Builder url = base().addPathSegments("open-apis/contact/v3/users")
                .addQueryParameter("page_size", String.valueOf(properties.getPageSize()))
                .addQueryParameter("department_id_type", DEPARTMENT_ID_TYPE)
                .addQueryParameter("user_id_type", USER_ID_TYPE);
        String pageToken = tokenOf(cursor);
        if (pageToken != null) {
            url.addQueryParameter("page_token", pageToken);
        }
        JsonNode node = execute(new Request.Builder().url(url.build()).get().build());
        return parseUsers(node, cursor);
    }

    @Override
    public Optional<FeishuUser> user(String openId) {
        if (blank(openId)) {
            return Optional.empty();
        }
        ensureToken();
        HttpUrl url = base().addPathSegments("open-apis/contact/v3/users/" + openId)
                .addQueryParameter("user_id_type", USER_ID_TYPE)
                .build();
        JsonNode node = execute(new Request.Builder().url(url).get().build());
        if (node.path("code").asInt(-1) != 0) {
            return Optional.empty();
        }
        JsonNode item = node.path("data").path("user");
        if (item.isMissingNode() || item.isNull() || item.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(parseUser(item));
    }

    @Override
    public Optional<FeishuDepartment> department(String openDepartmentId) {
        if (blank(openDepartmentId)) {
            return Optional.empty();
        }
        ensureToken();
        HttpUrl url = base().addPathSegments("open-apis/contact/v3/departments/" + openDepartmentId)
                .addQueryParameter("department_id_type", DEPARTMENT_ID_TYPE)
                .build();
        JsonNode node = execute(new Request.Builder().url(url).get().build());
        if (node.path("code").asInt(-1) != 0) {
            return Optional.empty();
        }
        JsonNode item = node.path("data").path("department");
        if (item.isMissingNode() || item.isNull() || item.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(parseDepartment(item));
    }

    // ---------- 内部：token ----------

    private void ensureToken() {
        if (tenantAccessToken != null && System.currentTimeMillis() < tokenExpireAtMillis) {
            return;
        }
        synchronized (tokenLock) {
            if (tenantAccessToken != null && System.currentTimeMillis() < tokenExpireAtMillis) {
                return;
            }
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("app_id", properties.getAppId());
            payload.put("app_secret", properties.getAppSecret());
            HttpUrl url = base()
                    .addPathSegments("open-apis/auth/v3/tenant_access_token/internal")
                    .build();
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(payload.toString(), JSON))
                    .build();
            JsonNode node = execute(request);
            String token = text(node, "tenant_access_token");
            if (blank(token)) {
                throw new BizException(ErrorCode.INTERNAL,
                        "飞书 tenant_access_token 获取失败: " + node.path("msg").asText(""));
            }
            long expireSeconds = node.path("expire").asLong(7200L);
            this.tenantAccessToken = token;
            this.tokenExpireAtMillis = System.currentTimeMillis()
                    + Math.max(1L, expireSeconds - 60L) * 1000L;
        }
    }

    // ---------- 内部：HTTP ----------

    private HttpUrl.Builder base() {
        HttpUrl parsed = HttpUrl.parse(properties.getBaseUrl());
        if (parsed == null) {
            throw new BizException(ErrorCode.INTERNAL, "飞书 baseUrl 非法: " + properties.getBaseUrl());
        }
        return parsed.newBuilder();
    }

    private JsonNode execute(Request request) {
        Request authorized = request.newBuilder()
                .header("Authorization", "Bearer " + (tenantAccessToken == null ? "" : tenantAccessToken))
                .build();
        int attempts = Math.max(1, properties.getMaxRetries() + 1);
        long backoff = Math.max(1L, properties.getBackoffMs());
        BizException last = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            if (attempt > 0) {
                sleep(backoff);
                backoff *= 2;
            }
            try (Response response = httpClient.newCall(authorized).execute()) {
                int code = response.code();
                String body = response.body() == null ? "" : response.body().string();
                if (code == 429 || code >= 500) {
                    last = new BizException(ErrorCode.INTERNAL,
                            "飞书接口限流/服务端错误: HTTP " + code);
                    continue;
                }
                if (!response.isSuccessful()) {
                    throw new BizException(ErrorCode.INTERNAL,
                            "飞书接口调用失败: HTTP " + code + " " + body);
                }
                try {
                    return objectMapper.readTree(body.isEmpty() ? "{}" : body);
                } catch (JsonProcessingException ex) {
                    throw new BizException(ErrorCode.INTERNAL,
                            "飞书接口响应解析失败: " + ex.getMessage());
                }
            } catch (IOException ex) {
                last = new BizException(ErrorCode.INTERNAL, "飞书接口网络异常: " + ex.getMessage());
            }
        }
        throw last != null ? last : new BizException(ErrorCode.INTERNAL, "飞书接口调用失败");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.INTERNAL, "飞书接口重试被中断");
        }
    }

    // ---------- 内部：解析 ----------

    private Page<FeishuDepartment> parseDepartments(JsonNode node, PageCursor cursor) {
        JsonNode data = node.path("data");
        List<FeishuDepartment> items = new ArrayList<>();
        for (JsonNode item : data.path("items")) {
            items.add(parseDepartment(item));
        }
        return new Page<>(items, nextCursor(data, cursor), data.path("has_more").asBoolean(false));
    }

    private Page<FeishuUser> parseUsers(JsonNode node, PageCursor cursor) {
        JsonNode data = node.path("data");
        List<FeishuUser> items = new ArrayList<>();
        for (JsonNode item : data.path("items")) {
            items.add(parseUser(item));
        }
        return new Page<>(items, nextCursor(data, cursor), data.path("has_more").asBoolean(false));
    }

    private PageCursor nextCursor(JsonNode data, PageCursor cursor) {
        PageCursor base = cursor == null ? PageCursor.first() : cursor;
        String nextToken = text(data, "page_token");
        return base.next(nextToken);
    }

    private FeishuDepartment parseDepartment(JsonNode item) {
        List<String> leaders = new ArrayList<>();
        for (JsonNode leader : item.path("leaders")) {
            String openId = text(leader, "open_id");
            if (!blank(openId)) {
                leaders.add(openId);
            }
        }
        return new FeishuDepartment(
                text(item, "open_department_id"),
                text(item, "parent_department_id"),
                text(item, "name"),
                item.path("order").asInt(0),
                leaders);
    }

    private FeishuUser parseUser(JsonNode item) {
        JsonNode status = item.path("status");
        boolean active = true;
        if (!status.isMissingNode() && !status.isNull() && !status.isEmpty()) {
            boolean activated = !status.has("is_activated") || status.path("is_activated").asBoolean(true);
            boolean frozen = status.has("is_frozen") && status.path("is_frozen").asBoolean(false);
            active = activated && !frozen;
        }
        List<String> departmentIds = new ArrayList<>();
        for (JsonNode department : item.path("department_ids")) {
            String id = department.asText(null);
            if (!blank(id)) {
                departmentIds.add(id);
            }
        }
        return new FeishuUser(
                text(item, "open_id"),
                text(item, "union_id"),
                text(item, "user_id"),
                text(item, "name"),
                text(item, "email"),
                text(item, "mobile"),
                text(item, "employee_no"),
                departmentIds,
                active,
                null);
    }

    private static String tokenOf(PageCursor cursor) {
        if (cursor == null) {
            return null;
        }
        String token = cursor.token();
        return blank(token) ? null : token;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
