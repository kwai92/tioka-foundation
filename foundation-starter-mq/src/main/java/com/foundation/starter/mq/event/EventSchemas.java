package com.foundation.starter.mq.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 事件契约注册表（ADP-02 / 36 号规划 §6「契约版本」）。
 *
 * <p>权威定义在构件内 {@code /contracts/event-schemas-v1.json}，随 starter 发布，
 * 非 Java 接入方可直接从 jar 取用；本类只做加载与查询，不另硬编码一份事件清单，避免双源漂移。
 *
 * <p>未登记的事件类型按 {@link DomainEvent#DEFAULT_SCHEMA_VERSION} 处理（前向兼容：新增事件
 * 不需要先改底座代码），但契约测试会要求把新事件补进注册表。
 */
public final class EventSchemas {

    /** 契约资源路径（classpath，随 jar 发布）。 */
    public static final String RESOURCE = "/contracts/event-schemas-v1.json";

    private static final EventSchemas INSTANCE = new EventSchemas();

    private final JsonNode root;
    private final Map<String, Integer> versions;

    private EventSchemas() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = EventSchemas.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("事件契约文件缺失：" + RESOURCE);
            }
            this.root = mapper.readTree(in);
        } catch (IOException ex) {
            throw new IllegalStateException("事件契约文件解析失败：" + RESOURCE, ex);
        }
        Map<String, Integer> loaded = new LinkedHashMap<>();
        for (JsonNode event : root.path("events")) {
            loaded.put(event.path("eventType").asText(), event.path("schemaVersion").asInt(1));
        }
        this.versions = Map.copyOf(loaded);
    }

    /** 当前契约主版本。 */
    public static int currentSchemaVersion() {
        return INSTANCE.root.path("schemaVersion").asInt(DomainEvent.DEFAULT_SCHEMA_VERSION);
    }

    /** 已登记的事件类型。 */
    public static Set<String> knownEventTypes() {
        return INSTANCE.versions.keySet();
    }

    /** 按事件类型取契约版本；未登记时回落到缺省版本。 */
    public static int versionOf(String eventType) {
        return INSTANCE.versions.getOrDefault(eventType, DomainEvent.DEFAULT_SCHEMA_VERSION);
    }

    /** 事件是否已登记（用于契约测试，不阻断运行时）。 */
    public static boolean isKnown(String eventType) {
        return INSTANCE.versions.containsKey(eventType);
    }

    /** Webhook envelope 的必需顶层字段。 */
    public static Set<String> envelopeRequiredFields() {
        java.util.LinkedHashSet<String> fields = new java.util.LinkedHashSet<>();
        for (JsonNode field : INSTANCE.root.path("webhookChannel").path("envelopeRequiredFields")) {
            fields.add(field.asText());
        }
        return Set.copyOf(fields);
    }
}
