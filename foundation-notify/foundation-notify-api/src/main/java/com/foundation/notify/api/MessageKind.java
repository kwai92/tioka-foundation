package com.foundation.notify.api;

/**
 * 消息类别（F-09 BAS-16 §数据模型）：站内信 / 待办 / 通知 / 公告展开项。
 *
 * <p>公告发布时按范围展开为一条 {@link #ANNOUNCEMENT} 的 {@code fdn_message}，已读复用
 * {@code fdn_message_receiver.read_at}，不单建公告已读表。
 */
public enum MessageKind {

    /** 站内信。 */
    INBOX,
    /** 待办（业务系统登记、门户聚合展示、点击深链回应用）。 */
    TODO,
    /** 通知。 */
    NOTICE,
    /** 公告展开项。 */
    ANNOUNCEMENT;

    /** 宽松解析；无法识别时返回 {@code null}（由调用方决定拒绝或取默认）。 */
    public static MessageKind of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (MessageKind kind : values()) {
            if (kind.name().equalsIgnoreCase(value.trim())) {
                return kind;
            }
        }
        return null;
    }
}
