package com.foundation.starter.client.authz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.client.integration.SnapshotInvalidationHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code fdn.authz.changed} 订阅入口（P1 运行平面）：把中台的授权变更事件转成缓存失效。
 *
 * <p>平台只提供这个纯 POJO 入口，不绑定 RocketMQ / Kafka / Webhook 任何具体通道：
 * 派生应用在自己的 consumer 或 Webhook 接收端点里调用 {@link #onMessage(String)} 即可。
 * 这样轻量路径不必引入 {@code foundation-starter-mq} 或服务端安全 starter。
 *
 * <p><b>不静默吞事件</b>：JSON 非法、缺 {@code personId} / {@code appCode} 时抛异常，
 * 让消息通道重试或告警——若把失效事件悄悄丢掉，缓存会一直用旧授权直到 TTL 到期。
 * 非本应用、非授权变更的事件返回 {@code false} 忽略（这是正常的多租户广播行为）。
 */
public final class AuthorizationSnapshotInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationSnapshotInvalidationListener.class);

    /** 订阅的事件名（与 {@code AuthzChangedEvent.EVENT_TYPE} 同源）。 */
    public static final String EVENT_TYPE = "fdn.authz.changed";

    private final String appCode;
    private final SnapshotInvalidationHook hook;
    private final ObjectMapper objectMapper;

    public AuthorizationSnapshotInvalidationListener(String appCode,
                                                     SnapshotInvalidationHook hook,
                                                     ObjectMapper objectMapper) {
        if (appCode == null || appCode.isBlank() || hook == null || objectMapper == null) {
            throw new IllegalArgumentException("appCode, hook and ObjectMapper are required");
        }
        this.appCode = appCode;
        this.hook = hook;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理一条事件 JSON。
     *
     * @return {@code true} 表示本应用的缓存已失效；{@code false} 表示事件被正常忽略
     * @throws IllegalStateException 事件结构非法时必须向上抛出，不得吞掉
     */
    public boolean onMessage(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("授权变更事件为空，缓存失效未执行");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException("解析授权变更事件失败：" + ex.getMessage(), ex);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalStateException("授权变更事件必须是 JSON 对象");
        }
        // MQ body 用 type，Webhook envelope 顶层用 eventType；两者都接受。
        String eventType = text(root, "type");
        if (eventType == null) {
            eventType = text(root, "eventType");
        }
        if (eventType != null && !EVENT_TYPE.equals(eventType)) {
            return false;
        }
        // Webhook envelope 把业务字段放在 payload 里，这里一并兼容。
        JsonNode body = root.has("payload") && root.get("payload").isObject()
                ? root.get("payload") : root;
        String personId = text(body, "personId");
        String eventAppCode = text(body, "appCode");
        if (personId == null || personId.isBlank()) {
            throw new IllegalStateException("授权变更事件缺 personId，缓存失效未执行");
        }
        if (eventAppCode == null || eventAppCode.isBlank()) {
            throw new IllegalStateException("授权变更事件缺 appCode，缓存失效未执行");
        }
        if (!appCode.equals(eventAppCode)) {
            return false;
        }
        hook.invalidate(personId);
        log.debug("授权快照已按事件失效：appCode={} personId={}", appCode, personId);
        return true;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }
}
