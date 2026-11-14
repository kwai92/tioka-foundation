package com.foundation.notify.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.notify.api.MessageTemplateApi;
import com.foundation.notify.api.MessageTemplateCmd;
import com.foundation.notify.api.MessageTemplateView;
import com.foundation.notify.domain.MessageTemplate;
import com.foundation.notify.infrastructure.MessageTemplateRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 渠道模板应用服务（F-09 BAS-16 §接口 {@code /api/fdn/message-templates}）。 */
public class MessageTemplateService implements MessageTemplateApi {

    private final MessageTemplateRepository templates;

    public MessageTemplateService(MessageTemplateRepository templates) {
        this.templates = templates;
    }

    @Override
    public List<MessageTemplateView> list(String appCode, String channel) {
        return templates.list(appCode, channel).stream().map(MessageTemplateService::toView).toList();
    }

    @Override
    @Transactional
    public MessageTemplateView create(MessageTemplateCmd cmd) {
        if (templates.exists(cmd.appCode(), cmd.code(), cmd.channel())) {
            throw new BizException(ErrorCode.CONFLICT,
                    "模板已存在：" + cmd.appCode() + "/" + cmd.code() + "/" + cmd.channel());
        }
        MessageTemplate template = MessageTemplate.create(cmd.appCode(), cmd.code(), cmd.channel(),
                cmd.titleTpl(), cmd.bodyTpl(), cmd.dataLabel());
        templates.save(template);
        return toView(template);
    }

    @Override
    @Transactional
    public MessageTemplateView update(String id, MessageTemplateCmd cmd) {
        MessageTemplate existing = templates.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "模板不存在：" + id));
        MessageTemplate updated = new MessageTemplate(existing.id(), existing.appCode(), existing.code(),
                existing.channel(),
                cmd.titleTpl() != null ? cmd.titleTpl() : existing.titleTpl(),
                cmd.bodyTpl() != null ? cmd.bodyTpl() : existing.bodyTpl(),
                cmd.dataLabel() != null ? cmd.dataLabel() : existing.dataLabel(),
                existing.status());
        templates.save(updated);
        return toView(updated);
    }

    private static MessageTemplateView toView(MessageTemplate template) {
        return new MessageTemplateView(template.id(), template.appCode(), template.code(), template.channel(),
                template.titleTpl(), template.bodyTpl(), template.dataLabel(), template.status());
    }
}
