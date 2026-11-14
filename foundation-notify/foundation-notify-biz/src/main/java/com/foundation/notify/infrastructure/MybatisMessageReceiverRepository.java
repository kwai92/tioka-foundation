package com.foundation.notify.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.notify.api.MessageKind;
import com.foundation.notify.domain.MessageReceiver;
import com.foundation.notify.infrastructure.mapper.MessageMapper;
import com.foundation.notify.infrastructure.mapper.MessageReceiverMapper;
import com.foundation.notify.infrastructure.persistence.MessageDO;
import com.foundation.notify.infrastructure.persistence.MessageReceiverDO;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 消息收件人仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisMessageReceiverRepository implements MessageReceiverRepository {

    private final MessageReceiverMapper mapper;
    private final MessageMapper messageMapper;

    public MybatisMessageReceiverRepository(MessageReceiverMapper mapper, MessageMapper messageMapper) {
        this.mapper = mapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public void save(MessageReceiver receiver) {
        MessageReceiverDO row = new MessageReceiverDO();
        row.setId(receiver.id());
        row.setMessageId(receiver.messageId());
        row.setPersonId(receiver.personId());
        row.setReadAt(receiver.readAt() == null ? null : LocalDateTime.ofInstant(receiver.readAt(), ZoneOffset.UTC));
        row.setDeliverStatus(receiver.deliverStatus());
        mapper.insert(row);
    }

    @Override
    public Optional<MessageReceiver> findByMessageAndPerson(String messageId, String personId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<MessageReceiverDO>lambdaQuery()
                        .eq(MessageReceiverDO::getMessageId, messageId)
                        .eq(MessageReceiverDO::getPersonId, personId)))
                .map(MybatisMessageReceiverRepository::toDomain);
    }

    @Override
    public List<MessageReceiver> pageByPerson(String personId, MessageKind kind, Boolean read, String cursor,
                                              int size) {
        return mapper.pageByPerson(personId, kind == null ? null : kind.name(), read, cursor, size).stream()
                .map(MybatisMessageReceiverRepository::toDomain).toList();
    }

    @Override
    public int markRead(String messageId, String personId, Instant readAt) {
        LocalDateTime utc = LocalDateTime.ofInstant(readAt, ZoneOffset.UTC);
        return mapper.update(null, Wrappers.<MessageReceiverDO>lambdaUpdate()
                .eq(MessageReceiverDO::getMessageId, messageId)
                .eq(MessageReceiverDO::getPersonId, personId)
                .isNull(MessageReceiverDO::getReadAt)
                .set(MessageReceiverDO::getReadAt, utc)
                .set(MessageReceiverDO::getUpdateAt, utc));
    }

    @Override
    public int markAllRead(String personId, MessageKind kind, Instant readAt) {
        LocalDateTime utc = LocalDateTime.ofInstant(readAt, ZoneOffset.UTC);
        if (kind == null) {
            return mapper.update(null, Wrappers.<MessageReceiverDO>lambdaUpdate()
                    .eq(MessageReceiverDO::getPersonId, personId)
                    .isNull(MessageReceiverDO::getReadAt)
                    .set(MessageReceiverDO::getReadAt, utc)
                    .set(MessageReceiverDO::getUpdateAt, utc));
        }
        List<MessageReceiverDO> unread = unreadRows(personId);
        if (unread.isEmpty()) {
            return 0;
        }
        Set<String> matchedMessageIds = messageIdsOfKind(unread, kind);
        List<String> ids = unread.stream()
                .filter(row -> matchedMessageIds.contains(row.getMessageId()))
                .map(MessageReceiverDO::getId)
                .toList();
        if (ids.isEmpty()) {
            return 0;
        }
        return mapper.update(null, Wrappers.<MessageReceiverDO>lambdaUpdate()
                .in(MessageReceiverDO::getId, ids)
                .isNull(MessageReceiverDO::getReadAt)
                .set(MessageReceiverDO::getReadAt, utc)
                .set(MessageReceiverDO::getUpdateAt, utc));
    }

    @Override
    public Map<String, Long> unreadByKind(String personId) {
        List<MessageReceiverDO> unread = unreadRows(personId);
        Map<String, Long> result = new LinkedHashMap<>();
        if (unread.isEmpty()) {
            return result;
        }
        Map<String, String> kindByMessageId = messageMapper
                .selectByIds(unread.stream().map(MessageReceiverDO::getMessageId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(MessageDO::getId, MessageDO::getKind, (a, b) -> a));
        for (MessageReceiverDO row : unread) {
            String kind = kindByMessageId.get(row.getMessageId());
            if (kind != null) {
                result.merge(kind, 1L, Long::sum);
            }
        }
        return result;
    }

    @Override
    public int deleteUnreadByMessage(String messageId) {
        return mapper.delete(Wrappers.<MessageReceiverDO>lambdaQuery()
                .eq(MessageReceiverDO::getMessageId, messageId)
                .isNull(MessageReceiverDO::getReadAt));
    }

    @Override
    public int deleteReadBefore(Instant before) {
        return mapper.deleteReadBefore(LocalDateTime.ofInstant(before, ZoneOffset.UTC));
    }

    private List<MessageReceiverDO> unreadRows(String personId) {
        return mapper.selectList(Wrappers.<MessageReceiverDO>lambdaQuery()
                .eq(MessageReceiverDO::getPersonId, personId)
                .isNull(MessageReceiverDO::getReadAt));
    }

    private Set<String> messageIdsOfKind(List<MessageReceiverDO> unread, MessageKind kind) {
        return messageMapper.selectByIds(unread.stream().map(MessageReceiverDO::getMessageId).distinct().toList())
                .stream()
                .filter(message -> kind.name().equals(message.getKind()))
                .map(MessageDO::getId)
                .collect(Collectors.toSet());
    }

    private static MessageReceiver toDomain(MessageReceiverDO row) {
        return new MessageReceiver(row.getId(), row.getMessageId(), row.getPersonId(),
                row.getReadAt() == null ? null : row.getReadAt().toInstant(ZoneOffset.UTC), row.getDeliverStatus());
    }
}
