package com.foundation.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.common.api.event.OutboxAppender;
import com.foundation.common.id.CommonIdPrefix;
import com.foundation.common.id.Ids;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

/** outbox 追加器的 JDBC 实现（写入 {@code fdn_outbox}，中继投递归 BAS-24）。 */
public class JdbcOutboxAppender implements OutboxAppender {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcOutboxAppender(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(String topic, String eventType, String aggregate, Map<String, Object> payload) {
        jdbc.update("INSERT INTO fdn_outbox (id, topic, event_type, aggregate, payload) VALUES (?, ?, ?, ?, ?::jsonb)",
                Ids.newId(CommonIdPrefix.OUTBOX_EVENT), topic, eventType, aggregate, toJson(payload));
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("事件体无法序列化", ex);
        }
    }
}
