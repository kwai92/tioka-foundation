package com.foundation.notify.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.api.event.EventProperties;
import com.foundation.notify.api.MessageKind;
import com.foundation.notify.api.MessagePageView;
import com.foundation.notify.api.MessageQuery;
import com.foundation.notify.api.MessageSendCmd;
import com.foundation.notify.api.MessageSentEvent;
import com.foundation.notify.api.MessageView;
import com.foundation.notify.api.UnreadCountView;
import com.foundation.notify.config.NotifyProperties;
import com.foundation.notify.domain.DataLabel;
import com.foundation.notify.infrastructure.MessageEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 消息中心应用服务（F-09 BAS-16 §接口 {@code /api/fdn/messages}）：投放去重、已读、未读数、事件。 */
class MessageServiceTest {

    private final TestDoubles.InMemoryMessageRepository messages = new TestDoubles.InMemoryMessageRepository();
    private final TestDoubles.InMemoryMessageReceiverRepository receivers =
            new TestDoubles.InMemoryMessageReceiverRepository(messages);
    private final TestDoubles.InMemoryMessageTemplateRepository templates =
            new TestDoubles.InMemoryMessageTemplateRepository();
    private final TestDoubles.RecordingOutbox outbox = new TestDoubles.RecordingOutbox();
    private final TestDoubles.RecordingChannel mailChannel =
            new TestDoubles.RecordingChannel("MAIL", DataLabel.INTERNAL);

    private MessageService service;

    @BeforeEach
    void setUp() {
        MessageEventPublisher events = new MessageEventPublisher(outbox, new EventProperties());
        UnreadCounter unreadCounter = new UnreadCounter(receivers, null);
        service = new MessageService(messages, receivers, templates,
                List.of(new InboxChannel(), mailChannel), unreadCounter, events, new NotifyProperties());
    }

    @Test
    void duplicateDedupeKeyReturnsSameMessageWithoutCreatingNew() {
        MessageSendCmd cmd = sendCmd(MessageKind.INBOX, "标题", null, "key-1", List.of("per_a"));

        MessageView first = service.send(cmd, "sys", "系统");
        MessageView second = service.send(cmd, "sys", "系统");

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(messages.byId).hasSize(1);
    }

    @Test
    void sendIncrementsUnreadThenMarkReadClearsItAndIsIdempotent() {
        MessageView sent = service.send(sendCmd(MessageKind.INBOX, "标题", null, "k", List.of("per_a")),
                "sys", "系统");

        UnreadCountView unread = service.unreadCount("per_a");
        assertThat(unread.total()).isEqualTo(1);
        assertThat(unread.byKind()).containsEntry("INBOX", 1L);

        MessageView first = service.markRead("per_a", sent.id());
        assertThat(first.read()).isTrue();
        assertThat(service.unreadCount("per_a").total()).isZero();

        MessageView second = service.markRead("per_a", sent.id());
        assertThat(second.readAt()).isEqualTo(first.readAt());
    }

    @Test
    void markReadUnknownMessageThrowsNotFound() {
        assertThatThrownBy(() -> service.markRead("per_a", "msg_missing"))
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void markReadMessageNotDeliveredToPersonThrowsNotFound() {
        MessageView sent = service.send(sendCmd(MessageKind.INBOX, "标题", null, "k", List.of("per_b")),
                "sys", "系统");

        assertThatThrownBy(() -> service.markRead("per_a", sent.id()))
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void sendAnnouncementKindIsRejected() {
        MessageSendCmd cmd = sendCmd(MessageKind.ANNOUNCEMENT, "标题", null, null, List.of("per_a"));

        assertThatThrownBy(() -> service.send(cmd, "sys", "系统"))
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void sendWithoutReceiversIsRejected() {
        MessageSendCmd cmd = sendCmd(MessageKind.INBOX, "标题", null, null, List.of());

        assertThatThrownBy(() -> service.send(cmd, "sys", "系统"))
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void mineReturnsMessageWithDeepLinkAndUnreadState() {
        MessageView sent = service.send(
                sendCmd(MessageKind.TODO, "待办", "/app/contract/123", null, List.of("per_a")), "sys", "系统");

        MessagePageView page = service.mine("per_a", new MessageQuery(MessageKind.TODO, null, null, 20));

        assertThat(page.items()).isNotEmpty();
        MessageView item = page.items().get(0);
        assertThat(item.id()).isEqualTo(sent.id());
        assertThat(item.link()).isEqualTo("/app/contract/123");
        assertThat(item.read()).isFalse();
    }

    @Test
    void markAllReadByKindOnlyClearsMatchingKind() {
        service.send(sendCmd(MessageKind.INBOX, "站内信", null, null, List.of("per_a")), "sys", "系统");
        service.send(sendCmd(MessageKind.TODO, "待办", null, null, List.of("per_a")), "sys", "系统");

        int affected = service.markAllRead("per_a", MessageKind.INBOX);

        assertThat(affected).isEqualTo(1);
        assertThat(service.unreadCount("per_a").byKind()).containsOnlyKeys("TODO");
        assertThat(service.unreadCount("per_a").byKind()).containsEntry("TODO", 1L);
    }

    @Test
    void sendPublishesMessageSentEventWithIdAndReceiverCount() {
        MessageView sent = service.send(
                sendCmd(MessageKind.INBOX, "标题", null, "k", List.of("per_a", "per_b")), "sys", "系统");

        assertThat(outbox.eventTypes).contains(MessageSentEvent.EVENT_TYPE);
        Map<String, Object> payload = outbox.payloads.get(0);
        assertThat(payload.get("messageId")).isEqualTo(sent.id());
        assertThat(payload.get("receiverCount")).isEqualTo(2);
    }

    private static MessageSendCmd sendCmd(MessageKind kind, String title, String link, String dedupeKey,
                                          List<String> receivers) {
        return new MessageSendCmd("fdn", kind, null, title, null, Map.of(), link,
                List.of("INBOX"), receivers, dedupeKey, null, null);
    }
}
