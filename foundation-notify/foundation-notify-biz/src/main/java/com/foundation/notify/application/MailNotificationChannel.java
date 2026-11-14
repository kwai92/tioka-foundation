package com.foundation.notify.application;

import com.foundation.notify.domain.DataLabel;
import com.foundation.notify.domain.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 邮件渠道：许可取 {@link DataLabel#INTERNAL}（INTERNAL 及以下允许外发）。
 *
 * <p>真实 SMTP 外发与 ADR-074 {@code ext_interaction} 台账由后续审计包接入，此处只做日志占位。
 */
public class MailNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(MailNotificationChannel.class);

    @Override
    public String code() {
        return "MAIL";
    }

    @Override
    public DataLabel clearance() {
        return DataLabel.INTERNAL;
    }

    @Override
    public int deliver(Message message, List<String> receiverIds) {
        log.info("邮件渠道投递占位：消息 {} 收件人 {} 人", message.id(), receiverIds.size());
        return receiverIds.size();
    }
}
