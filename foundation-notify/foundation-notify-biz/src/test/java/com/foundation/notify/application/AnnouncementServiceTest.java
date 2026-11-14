package com.foundation.notify.application;

import com.foundation.authz.api.RoleGrantView;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.api.event.EventProperties;
import com.foundation.notify.api.AnnouncementCmd;
import com.foundation.notify.api.AnnouncementPublishedEvent;
import com.foundation.notify.api.AnnouncementScopeType;
import com.foundation.notify.api.AnnouncementStatus;
import com.foundation.notify.api.AnnouncementView;
import com.foundation.notify.infrastructure.AnnouncementEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 公告应用服务（F-09 BAS-16 §接口 {@code /api/fdn/announcements}）：范围展开、发布、下线撤回。 */
class AnnouncementServiceTest {

    private final TestDoubles.InMemoryAnnouncementRepository announcements =
            new TestDoubles.InMemoryAnnouncementRepository();
    private final TestDoubles.InMemoryMessageRepository messages = new TestDoubles.InMemoryMessageRepository();
    private final TestDoubles.InMemoryMessageReceiverRepository receivers =
            new TestDoubles.InMemoryMessageReceiverRepository(messages);
    private final TestDoubles.StubPersonApi persons = new TestDoubles.StubPersonApi();
    private final TestDoubles.StubGrantApi grants = new TestDoubles.StubGrantApi();
    private final TestDoubles.RecordingOutbox outbox = new TestDoubles.RecordingOutbox();

    private AnnouncementService service;

    @BeforeEach
    void setUp() {
        persons.allPersons.addAll(List.of("per_1", "per_2"));
        persons.orgPersons.put("ou_1", Set.of("per_3"));
        grants.grants.add(new RoleGrantView("grt_1", "admin", "PERSON", "per_4", false, null, null, "MANUAL"));

        AnnouncementEventPublisher events = new AnnouncementEventPublisher(outbox, new EventProperties());
        UnreadCounter unreadCounter = new UnreadCounter(receivers, null);
        service = new AnnouncementService(announcements, messages, receivers, persons, grants, events, unreadCounter);
    }

    @Test
    void globalPublishExpandsToAllPersonsAndEmitsEvent() {
        AnnouncementView draft = create(AnnouncementScopeType.GLOBAL, null);

        AnnouncementView published = service.publish(draft.id());

        String messageId = messageIdOf(draft.id());
        assertThat(receivers.countByMessage(messageId)).isEqualTo(2);
        assertThat(published.status()).isEqualTo(AnnouncementStatus.PUBLISHED);
        assertThat(outbox.eventTypes).contains(AnnouncementPublishedEvent.EVENT_TYPE);
        assertThat(outbox.payloads.get(0).get("receiverCount")).isEqualTo(2);
    }

    @Test
    void orgPublishExpandsToOrgUnitPersons() {
        AnnouncementView draft = create(AnnouncementScopeType.ORG, "ou_1");

        service.publish(draft.id());

        assertThat(receivers.countByMessage(messageIdOf(draft.id()))).isEqualTo(1);
        assertThat(receivers.all()).extracting(row -> row.personId()).containsExactly("per_3");
    }

    @Test
    void rolePublishExpandsToGrantedPersons() {
        AnnouncementView draft = create(AnnouncementScopeType.ROLE, "admin");

        service.publish(draft.id());

        assertThat(receivers.countByMessage(messageIdOf(draft.id()))).isEqualTo(1);
        assertThat(receivers.all()).extracting(row -> row.personId()).containsExactly("per_4");
    }

    @Test
    void offlineRemovesUnreadButKeepsRead() {
        AnnouncementView draft = create(AnnouncementScopeType.GLOBAL, null);
        service.publish(draft.id());
        String messageId = messageIdOf(draft.id());
        receivers.markRead(messageId, "per_1", Instant.now());

        service.offline(draft.id());

        assertThat(receivers.all()).hasSize(1);
        assertThat(receivers.all().get(0).personId()).isEqualTo("per_1");
        assertThat(receivers.all().get(0).readAt()).isNotNull();
    }

    @Test
    void updateAfterPublishThrowsConflict() {
        AnnouncementView draft = create(AnnouncementScopeType.GLOBAL, null);
        service.publish(draft.id());

        assertThatThrownBy(() -> service.update(draft.id(),
                new AnnouncementCmd("新标题", "新正文", AnnouncementScopeType.GLOBAL, null, null, null, null)))
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void publishTwiceThrowsConflict() {
        AnnouncementView draft = create(AnnouncementScopeType.GLOBAL, null);
        service.publish(draft.id());

        assertThatThrownBy(() -> service.publish(draft.id()))
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    private AnnouncementView create(AnnouncementScopeType scopeType, String scopeId) {
        return service.create(new AnnouncementCmd("标题", "正文", scopeType, scopeId, null, null, null), "pub_1");
    }

    private String messageIdOf(String announcementId) {
        return messages.findByDedupeKey("fdn", "announcement:" + announcementId).orElseThrow().id();
    }
}
