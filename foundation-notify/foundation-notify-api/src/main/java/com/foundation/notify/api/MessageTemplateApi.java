package com.foundation.notify.api;

import java.util.List;

/**
 * 渠道模板契约（F-09 BAS-16 §接口 {@code GET/POST/PUT /api/fdn/message-templates}）。
 *
 * <p>权限码 {@code fdn:notify:send}。
 */
public interface MessageTemplateApi {

    /** 模板列表（按应用 / 渠道过滤，均可空）。 */
    List<MessageTemplateView> list(String appCode, String channel);

    /** 新建模板；{@code (appCode, code, channel)} 冲突返回 {@code CONFLICT}。 */
    MessageTemplateView create(MessageTemplateCmd cmd);

    /** 更新模板标题 / 正文 / 数据标签。 */
    MessageTemplateView update(String id, MessageTemplateCmd cmd);
}
