package com.foundation.notify.domain;

import com.foundation.common.id.Ids;

import java.time.Instant;

/**
 * 消息收件人（F-09 BAS-16 §数据模型 {@code fdn_message_receiver}）：决定已读 / 未读与投递状态。
 *
 * <p>已读复用本站内收件行（{@code readAt}），公告展开项不单建已读表。
 */
public record MessageReceiver(String id,
                              String messageId,
                              String personId,
                              Instant readAt,
                              String deliverStatus) {

    public static final String DELIVER_PENDING = "PENDING";
    public static final String DELIVER_SENT = "SENT";
    public static final String DELIVER_FAILED = "FAILED";

    /** 新建收件行；站内即时落库，投递状态直接置 {@link #DELIVER_SENT}。 */
    public static MessageReceiver sent(String messageId, String personId) {
        return new MessageReceiver(Ids.newId(NotifyIdPrefix.RECEIVER), messageId, personId, null, DELIVER_SENT);
    }
}
