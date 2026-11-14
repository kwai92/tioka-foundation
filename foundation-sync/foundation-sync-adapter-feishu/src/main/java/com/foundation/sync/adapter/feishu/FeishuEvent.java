package com.foundation.sync.adapter.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.sync.spi.SyncObjectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 飞书事件回调解析结果（F-08 BAS-12）。
 *
 * @param objectType 事件涉及的对象类型
 * @param externalIds 受影响的外部对象 ID（可能为空）
 * @param deleted    是否为删除事件
 */
public record FeishuEvent(SyncObjectType objectType, List<String> externalIds, boolean deleted) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public FeishuEvent {
        externalIds = externalIds == null ? List.of() : List.copyOf(externalIds);
    }

    /**
     * 解析飞书回调体（形如 {@code {"header":{"event_type":"..."},"event":{...}}}）。
     *
     * <p>{@code event_type} 含 {@code department} → {@link SyncObjectType#ORG_UNIT}，
     * 含 {@code user} → {@link SyncObjectType#PERSON}；{@code deleted} 取 {@code event_type} 是否含
     * {@code deleted}；externalIds 取 {@code event.object.open_department_id} 或
     * {@code event.object.open_id}（缺失则空列表）。解析失败返回 {@link Optional#empty()}。
     */
    public static Optional<FeishuEvent> parse(String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = MAPPER.readTree(jsonBody);
            String eventType = root.path("header").path("event_type").asText("");
            if (eventType.isBlank()) {
                eventType = root.path("event_type").asText("");
            }
            String lower = eventType == null ? "" : eventType.toLowerCase();
            SyncObjectType objectType;
            if (lower.contains("department")) {
                objectType = SyncObjectType.ORG_UNIT;
            } else if (lower.contains("user")) {
                objectType = SyncObjectType.PERSON;
            } else {
                return Optional.empty();
            }
            List<String> externalIds = new ArrayList<>();
            JsonNode object = root.path("event").path("object");
            String departmentId = text(object, "open_department_id");
            if (departmentId != null) {
                externalIds.add(departmentId);
            }
            String openId = text(object, "open_id");
            if (openId != null) {
                externalIds.add(openId);
            }
            boolean deleted = lower.contains("deleted");
            return Optional.of(new FeishuEvent(objectType, externalIds, deleted));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
