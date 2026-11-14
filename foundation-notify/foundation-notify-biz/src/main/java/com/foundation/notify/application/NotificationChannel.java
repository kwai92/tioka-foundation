package com.foundation.notify.application;

import com.foundation.notify.domain.DataLabel;
import com.foundation.notify.domain.Message;

import java.util.List;

/**
 * 通知渠道 SPI（ADR-026）：站内信 / 邮件 / 飞书等外发渠道以插件形式接入。
 *
 * <p>渠道各自声明去向许可（ADR-040）：消息数据标签不高于许可才允许经该渠道外发。
 */
public interface NotificationChannel {

    /** 渠道码：{@code INBOX} / {@code MAIL} / {@code FEISHU}。 */
    String code();

    /** 渠道去向许可（ADR-040）：消息数据标签不高于许可才允许外发。 */
    DataLabel clearance();

    /** 实际外发；返回投递的收件人数（0 表示未投递）。不抛异常，失败以返回 0 表示。 */
    int deliver(Message message, List<String> receiverIds);
}
