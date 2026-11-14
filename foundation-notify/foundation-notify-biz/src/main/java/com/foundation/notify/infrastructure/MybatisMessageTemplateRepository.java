package com.foundation.notify.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.notify.domain.MessageTemplate;
import com.foundation.notify.infrastructure.mapper.MessageTemplateMapper;
import com.foundation.notify.infrastructure.persistence.MessageTemplateDO;

import java.util.List;
import java.util.Optional;

/** 渠道模板仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisMessageTemplateRepository implements MessageTemplateRepository {

    private final MessageTemplateMapper mapper;

    public MybatisMessageTemplateRepository(MessageTemplateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<MessageTemplate> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisMessageTemplateRepository::toDomain);
    }

    @Override
    public Optional<MessageTemplate> findActive(String appCode, String code, String channel) {
        boolean global = appCode == null || appCode.isBlank();
        return Optional.ofNullable(mapper.selectOne(Wrappers.<MessageTemplateDO>lambdaQuery()
                        .eq(MessageTemplateDO::getCode, code)
                        .eq(MessageTemplateDO::getChannel, channel)
                        .eq(MessageTemplateDO::getStatus, MessageTemplate.STATUS_ACTIVE)
                        .isNull(global, MessageTemplateDO::getAppCode)
                        .eq(!global, MessageTemplateDO::getAppCode, appCode)))
                .map(MybatisMessageTemplateRepository::toDomain);
    }

    @Override
    public List<MessageTemplate> list(String appCode, String channel) {
        return mapper.selectList(Wrappers.<MessageTemplateDO>lambdaQuery()
                        .eq(appCode != null && !appCode.isBlank(), MessageTemplateDO::getAppCode, appCode)
                        .eq(channel != null && !channel.isBlank(), MessageTemplateDO::getChannel, channel)
                        .orderByAsc(MessageTemplateDO::getId))
                .stream().map(MybatisMessageTemplateRepository::toDomain).toList();
    }

    @Override
    public boolean exists(String appCode, String code, String channel) {
        boolean global = appCode == null || appCode.isBlank();
        return mapper.selectCount(Wrappers.<MessageTemplateDO>lambdaQuery()
                .eq(MessageTemplateDO::getCode, code)
                .eq(MessageTemplateDO::getChannel, channel)
                .isNull(global, MessageTemplateDO::getAppCode)
                .eq(!global, MessageTemplateDO::getAppCode, appCode)) > 0;
    }

    @Override
    public void save(MessageTemplate template) {
        MessageTemplateDO row = new MessageTemplateDO();
        row.setId(template.id());
        row.setAppCode(template.appCode());
        row.setCode(template.code());
        row.setChannel(template.channel());
        row.setTitleTpl(template.titleTpl());
        row.setBodyTpl(template.bodyTpl());
        row.setDataLabel(template.dataLabel());
        row.setStatus(template.status());

        if (mapper.selectById(template.id()) == null) {
            mapper.insert(row);
        } else {
            mapper.updateById(row);
        }
    }

    private static MessageTemplate toDomain(MessageTemplateDO row) {
        return new MessageTemplate(row.getId(), row.getAppCode(), row.getCode(), row.getChannel(),
                row.getTitleTpl(), row.getBodyTpl(), row.getDataLabel(), row.getStatus());
    }
}
