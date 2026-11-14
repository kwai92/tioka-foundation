package com.foundation.notify.api;

/**
 * 渠道模板视图（F-09 BAS-16 §接口）。
 *
 * @param id        模板 ID（{@code mtp_…}）
 * @param appCode   所属应用编码，可空
 * @param code      模板编码
 * @param channel   渠道
 * @param titleTpl  标题模板
 * @param bodyTpl   正文模板
 * @param dataLabel 数据标签
 * @param status    状态（{@code ACTIVE} / {@code OFFLINE}）
 */
public record MessageTemplateView(String id,
                                  String appCode,
                                  String code,
                                  String channel,
                                  String titleTpl,
                                  String bodyTpl,
                                  String dataLabel,
                                  String status) {
}
