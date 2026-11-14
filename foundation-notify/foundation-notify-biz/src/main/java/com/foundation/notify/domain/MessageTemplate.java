package com.foundation.notify.domain;

import com.foundation.common.id.Ids;

/**
 * 渠道模板（F-09 BAS-16 §数据模型 {@code fdn_message_template}）。
 *
 * <p>{@code (appCode, code, channel)} 唯一；{@code appCode} 为空表示全局模板。
 */
public record MessageTemplate(String id,
                              String appCode,
                              String code,
                              String channel,
                              String titleTpl,
                              String bodyTpl,
                              String dataLabel,
                              String status) {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_OFFLINE = "OFFLINE";

    /** 新建模板：生成 ID，默认 {@code ACTIVE} 与 {@code INTERNAL}。 */
    public static MessageTemplate create(String appCode, String code, String channel, String titleTpl,
                                         String bodyTpl, String dataLabel) {
        return new MessageTemplate(
                Ids.newId(NotifyIdPrefix.TEMPLATE),
                appCode,
                code,
                channel,
                titleTpl,
                bodyTpl,
                dataLabel == null || dataLabel.isBlank() ? "INTERNAL" : dataLabel,
                STATUS_ACTIVE);
    }
}
