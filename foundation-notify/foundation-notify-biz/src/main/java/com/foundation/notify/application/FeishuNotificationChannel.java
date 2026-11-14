package com.foundation.notify.application;

import com.foundation.notify.domain.DataLabel;
import com.foundation.notify.domain.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 飞书渠道：许可取 {@link DataLabel#INTERNAL}（INTERNAL 及以下允许外发）。
 *
 * <p>真实飞书消息外发由后续集成包接入，此处只做日志占位。
 */
public class FeishuNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(FeishuNotificationChannel.class);

    @Override
    public String code() {
        return "FEISHU";
    }

    @Override
    public DataLabel clearance() {
        return DataLabel.INTERNAL;
    }

    @Override
    public int deliver(Message message, List<String> receiverIds) {
        log.info("飞书渠道投递占位：消息 {} 收件人 {} 人", message.id(), receiverIds.size());
        return receiverIds.size();
    }
}
