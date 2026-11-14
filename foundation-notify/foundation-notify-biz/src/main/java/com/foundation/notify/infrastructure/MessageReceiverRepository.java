package com.foundation.notify.infrastructure;

import com.foundation.notify.api.MessageKind;
import com.foundation.notify.domain.MessageReceiver;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 消息收件人仓储：已读状态、游标分页、未读聚合与保留期清理。 */
public interface MessageReceiverRepository {

    void save(MessageReceiver receiver);

    Optional<MessageReceiver> findByMessageAndPerson(String messageId, String personId);

    List<MessageReceiver> pageByPerson(String personId, MessageKind kind, Boolean read, String cursor, int size);

    int markRead(String messageId, String personId, Instant readAt);

    int markAllRead(String personId, MessageKind kind, Instant readAt);

    Map<String, Long> unreadByKind(String personId);

    int deleteUnreadByMessage(String messageId);

    int deleteReadBefore(Instant before);
}
