package com.foundation.notify.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.notify.api.MessageKind;
import com.foundation.notify.api.MessagePriority;
import com.foundation.notify.domain.Message;
import com.foundation.notify.infrastructure.mapper.MessageMapper;
import com.foundation.notify.infrastructure.persistence.MessageDO;

import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 消息主体仓储的 MyBatis-Plus 实现（48-CS §4）；{@code payload} 用 {@link ObjectMapper} 编解码。 */
public class MybatisMessageRepository implements MessageRepository {

    private final MessageMapper mapper;
    private final ObjectMapper json;

    public MybatisMessageRepository(MessageMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    public Optional<Message> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Message> findByDedupeKey(String appCode, String dedupeKey) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<MessageDO>lambdaQuery()
                        .eq(MessageDO::getAppCode, appCode)
                        .eq(MessageDO::getDedupeKey, dedupeKey)))
                .map(this::toDomain);
    }

    @Override
    public List<Message> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectByIds(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public void save(Message message) {
        MessageDO row = new MessageDO();
        row.setId(message.id());
        row.setAppCode(message.appCode());
        row.setKind(message.kind().name());
        row.setTitle(message.title());
        row.setBody(message.body());
        row.setTemplateCode(message.templateCode());
        row.setPayload(writePayload(message.payload()));
        row.setLink(message.link());
        row.setPriority(message.priority().name());
        row.setSenderId(message.senderId());
        row.setSenderName(message.senderName());
        row.setChannels(message.channels() == null ? null : String.join(",", message.channels()));
        row.setDedupeKey(message.dedupeKey());
        row.setDataLabel(message.dataLabel());

        if (mapper.selectById(message.id()) == null) {
            mapper.insert(row);
        } else {
            mapper.updateById(row);
        }
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return json.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("消息载荷序列化失败", ex);
        }
    }

    private Map<String, Object> readPayload(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(text, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("消息载荷反序列化失败", ex);
        }
    }

    private static List<String> splitChannels(String channels) {
        if (channels == null || channels.isBlank()) {
            return List.of();
        }
        return List.of(channels.split(","));
    }

    private Message toDomain(MessageDO row) {
        return new Message(row.getId(), row.getAppCode(), MessageKind.of(row.getKind()), row.getTitle(),
                row.getBody(), row.getTemplateCode(), readPayload(row.getPayload()), row.getLink(),
                MessagePriority.of(row.getPriority()), row.getSenderId(), row.getSenderName(),
                splitChannels(row.getChannels()), row.getDedupeKey(), row.getDataLabel(),
                row.getCreateAt() == null ? null : row.getCreateAt().toInstant(ZoneOffset.UTC));
    }
}
