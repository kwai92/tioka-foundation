package com.foundation.notify.infrastructure;

import com.foundation.notify.domain.Message;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 消息主体仓储（MyBatis-Plus 实现，48-CS §4）。 */
public interface MessageRepository {

    Optional<Message> findById(String id);

    Optional<Message> findByDedupeKey(String appCode, String dedupeKey);

    List<Message> findByIds(Collection<String> ids);

    void save(Message message);
}
