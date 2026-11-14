package com.foundation.notify.api;

/**
 * 消息契约（F-09 BAS-16 §接口）：发送 / 登记待办、我的消息、已读、未读数。
 *
 * <p>写接口权限码 {@code fdn:notify:send}；读接口要求登录（按 {@code personId} 隔离）。
 */
public interface MessageApi {

    /** 发送站内信 / 通知、登记待办；按 {@code (appCode, dedupeKey)} 幂等。 */
    MessageView send(MessageSendCmd cmd, String senderId, String senderName);

    /** 我的消息（按 {@code personId}，游标分页）。 */
    MessagePageView mine(String personId, MessageQuery query);

    /** 标记已读（幂等）；返回更新后的消息视图。 */
    MessageView markRead(String personId, String messageId);

    /** 全部标记已读；{@code kind} 为空表示全部类别。返回受影响条数。 */
    int markAllRead(String personId, MessageKind kind);

    /** 未读数（{@code {total, byKind}}）。 */
    UnreadCountView unreadCount(String personId);
}
