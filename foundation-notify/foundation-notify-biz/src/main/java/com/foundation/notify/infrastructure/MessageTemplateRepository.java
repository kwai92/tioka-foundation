package com.foundation.notify.infrastructure;

import com.foundation.notify.domain.MessageTemplate;

import java.util.List;
import java.util.Optional;

/** 渠道模板仓储。 */
public interface MessageTemplateRepository {

    Optional<MessageTemplate> findById(String id);

    Optional<MessageTemplate> findActive(String appCode, String code, String channel);

    List<MessageTemplate> list(String appCode, String channel);

    boolean exists(String appCode, String code, String channel);

    void save(MessageTemplate template);
}
