package com.foundation.notify.application;

import com.foundation.notify.domain.DataLabel;
import com.foundation.notify.domain.Message;

import java.util.List;

/**
 * 站内信渠道：站内即落库，无外发动作。
 *
 * <p>许可取 {@link DataLabel#SECRET}（最高），保证任何数据标签的站内信都保留在内网门户。
 */
public class InboxChannel implements NotificationChannel {

    @Override
    public String code() {
        return "INBOX";
    }

    @Override
    public DataLabel clearance() {
        return DataLabel.SECRET;
    }

    @Override
    public int deliver(Message message, List<String> receiverIds) {
        // 站内信直接落库（收件行已写入），此处无需外发。
        return receiverIds.size();
    }
}
