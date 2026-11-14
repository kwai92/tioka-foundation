package com.foundation.notify.application;

import com.foundation.common.api.event.EventProperties;
import com.foundation.notify.api.MessageKind;
import com.foundation.notify.api.MessageSendCmd;
import com.foundation.notify.api.MessageView;
import com.foundation.notify.config.NotifyProperties;
import com.foundation.notify.domain.DataLabel;
import com.foundation.notify.infrastructure.MessageEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 渠道数据标签过滤（ADR-040）：消息密级不高于渠道去向许可才允许外发。 */
class MessageChannelTest {

    private final TestDoubles.InMemoryMessageRepository messages = new TestDoubles.InMemoryMessageRepository();
    private final TestDoubles.InMemoryMessageReceiverRepository receivers =
            new TestDoubles.InMemoryMessageReceiverRepository(messages);
    private final TestDoubles.InMemoryMessageTemplateRepository templates =
            new TestDoubles.InMemoryMessageTemplateRepository();
    private final TestDoubles.RecordingOutbox outbox = new TestDoubles.RecordingOutbox();

    private TestDoubles.RecordingChannel mailChannel;
    private MessageService service;

    @BeforeEach
    void setUp() {
        mailChannel = new TestDoubles.RecordingChannel("MAIL", DataLabel.INTERNAL);
        MessageEventPublisher events = new MessageEventPublisher(outbox, new EventProperties());
        UnreadCounter unreadCounter = new UnreadCounter(receivers, null);
        service = new MessageService(messages, receivers, templates,
                List.of(new InboxChannel(), mailChannel), unreadCounter, events, new NotifyProperties());
    }

    @Test
    void labelAboveClearanceSkipsExternalChannelButKeepsInbox() {
        MessageView sent = service.send(cmd("CONFIDENTIAL"), "sys", "系统");

        assertThat(mailChannel.deliveredMessageIds).isEmpty();
        assertThat(messages.byId.get(sent.id()).channels()).containsExactly("INBOX");
    }

    @Test
    void labelWithinClearanceDeliversToExternalChannel() {
        MessageView sent = service.send(cmd("INTERNAL"), "sys", "系统");

        assertThat(mailChannel.deliveredMessageIds).containsExactly(sent.id());
        assertThat(messages.byId.get(sent.id()).channels()).contains("MAIL");
    }

    private static MessageSendCmd cmd(String dataLabel) {
        return new MessageSendCmd("fdn", MessageKind.INBOX, null, "标题", null, Map.of(), null,
                List.of("INBOX", "MAIL"), List.of("per_a"), null, null, dataLabel);
    }
}
