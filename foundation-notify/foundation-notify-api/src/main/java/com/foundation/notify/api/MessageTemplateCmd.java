package com.foundation.notify.api;

/**
 * 渠道模板命令（F-09 BAS-16 §接口 {@code POST/PUT /api/fdn/message-templates}）。
 *
 * @param appCode   所属应用编码，可空（全局模板）
 * @param code      模板编码
 * @param channel   渠道（{@code INBOX} / {@code MAIL} / {@code FEISHU}）
 * @param titleTpl  标题模板
 * @param bodyTpl   正文模板
 * @param dataLabel 数据标签；空取 {@code INTERNAL}
 */
public record MessageTemplateCmd(String appCode,
                                 String code,
                                 String channel,
                                 String titleTpl,
                                 String bodyTpl,
                                 String dataLabel) {
}
