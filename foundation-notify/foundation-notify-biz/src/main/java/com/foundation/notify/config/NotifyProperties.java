package com.foundation.notify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** 消息中心配置（F-09 BAS-16 §配置项 {@code foundation.notify}）。 */
@ConfigurationProperties(prefix = "foundation.notify")
public class NotifyProperties {

    /** 默认投递渠道；发送命令未指定渠道时取此值。 */
    private List<String> channels = new ArrayList<>(List.of("INBOX"));

    /** 站内信保留期。 */
    private Inbox inbox = new Inbox();

    public List<String> getChannels() {
        return channels;
    }

    public void setChannels(List<String> channels) {
        this.channels = channels;
    }

    public Inbox getInbox() {
        return inbox;
    }

    public void setInbox(Inbox inbox) {
        this.inbox = inbox;
    }

    /** 站内信保留期配置。 */
    public static class Inbox {

        /** 已读站内信保留天数；{@code <= 0} 表示不清理。 */
        private int retentionDays = 365;

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }
    }
}
