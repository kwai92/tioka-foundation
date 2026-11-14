package com.foundation.notify.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 消息视图（F-09 BAS-16 §接口）。
 *
 * <p>{@code read} / {@code readAt} 为该收件人的已读状态（「我的消息」列表）；发送响应中为未读。
 *
 * @param id           消息 ID（{@code msg_…}）
 * @param appCode      发送方应用编码
 * @param kind         消息类别
 * @param title        标题
 * @param body         正文
 * @param templateCode 模板编码
 * @param payload      业务载荷
 * @param link         深链（只带业务对象 ID）
 * @param priority     优先级
 * @param senderId     发送人 ID，可空
 * @param senderName   发送人名称，可空
 * @param channels     投递渠道集合
 * @param dataLabel    数据标签
 * @param createAt     创建时间
 * @param read         当前收件人是否已读
 * @param readAt       已读时间，可空
 */
public record MessageView(String id,
                          String appCode,
                          MessageKind kind,
                          String title,
                          String body,
                          String templateCode,
                          Map<String, Object> payload,
                          String link,
                          MessagePriority priority,
                          String senderId,
                          String senderName,
                          List<String> channels,
                          String dataLabel,
                          Instant createAt,
                          boolean read,
                          Instant readAt) {
}
