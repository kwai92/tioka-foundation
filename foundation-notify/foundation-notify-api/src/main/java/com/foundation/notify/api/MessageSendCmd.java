package com.foundation.notify.api;

import java.util.List;
import java.util.Map;

/**
 * 发送站内信 / 通知、登记待办（F-09 BAS-16 §接口 {@code POST /api/fdn/messages}）。
 *
 * <p>权限码 {@code fdn:notify:send}；按 {@code (appCode, dedupeKey)} 幂等，同键重复投放返回原消息。
 *
 * @param appCode      发送方应用编码
 * @param kind         消息类别；{@link MessageKind#ANNOUNCEMENT} 由公告发布生成，不由此接口直投
 * @param templateCode 模板编码（按渠道渲染标题 / 正文），可空
 * @param title        标题；模板缺省时必填
 * @param body         正文，可空
 * @param payload      业务载荷（模板渲染变量与业务上下文）
 * @param link         深链：只带业务对象 ID（ADR-029 第 57 条）
 * @param channels     渠道集合；空表示取配置默认（{@code foundation.notify.channels}）
 * @param receivers    收件人人员 ID 集合
 * @param dedupeKey    去重键，可空（为空则不去重）
 * @param priority     优先级；空取 {@link MessagePriority#NORMAL}
 * @param dataLabel    数据标签；空取 {@code INTERNAL}（ADR-040）
 */
public record MessageSendCmd(String appCode,
                             MessageKind kind,
                             String templateCode,
                             String title,
                             String body,
                             Map<String, Object> payload,
                             String link,
                             List<String> channels,
                             List<String> receivers,
                             String dedupeKey,
                             MessagePriority priority,
                             String dataLabel) {
}
