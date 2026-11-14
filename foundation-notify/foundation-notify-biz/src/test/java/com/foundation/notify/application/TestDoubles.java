package com.foundation.notify.application;

import com.foundation.authz.api.GrantApi;
import com.foundation.authz.api.RoleGrantCmd;
import com.foundation.authz.api.RoleGrantView;
import com.foundation.common.api.event.OutboxAppender;
import com.foundation.notify.api.AnnouncementScopeType;
import com.foundation.notify.api.AnnouncementStatus;
import com.foundation.notify.api.MessageKind;
import com.foundation.notify.domain.Announcement;
import com.foundation.notify.domain.DataLabel;
import com.foundation.notify.domain.Message;
import com.foundation.notify.domain.MessageReceiver;
import com.foundation.notify.domain.MessageTemplate;
import com.foundation.notify.infrastructure.AnnouncementRepository;
import com.foundation.notify.infrastructure.MessageReceiverRepository;
import com.foundation.notify.infrastructure.MessageRepository;
import com.foundation.notify.infrastructure.MessageTemplateRepository;
import com.foundation.org.api.PersonApi;
import com.foundation.org.api.dto.PersonView;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** F-09 BAS-16 消息中心应用层单测用的内存仓储与桩（对齐 foundation-authz / foundation-org 的测试风格）。 */
final class TestDoubles {

    private TestDoubles() {
    }

    static final class InMemoryMessageRepository implements MessageRepository {

        final Map<String, Message> byId = new LinkedHashMap<>();

        @Override
        public Optional<Message> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Message> findByDedupeKey(String appCode, String dedupeKey) {
            if (dedupeKey == null || dedupeKey.isBlank()) {
                return Optional.empty();
            }
            return byId.values().stream()
                    .filter(message -> Objects.equals(message.appCode(), appCode))
                    .filter(message -> dedupeKey.equals(message.dedupeKey()))
                    .findFirst();
        }

        @Override
        public List<Message> findByIds(Collection<String> ids) {
            List<Message> found = new ArrayList<>();
            for (String id : ids) {
                Message message = byId.get(id);
                if (message != null) {
                    found.add(message);
                }
            }
            return found;
        }

        @Override
        public void save(Message message) {
            byId.put(message.id(), message);
        }
    }

    static final class InMemoryMessageReceiverRepository implements MessageReceiverRepository {

        private final InMemoryMessageRepository messages;
        final List<MessageReceiver> rows = new ArrayList<>();

        InMemoryMessageReceiverRepository(InMemoryMessageRepository messages) {
            this.messages = messages;
        }

        int countByMessage(String messageId) {
            return (int) rows.stream().filter(row -> row.messageId().equals(messageId)).count();
        }

        List<MessageReceiver> all() {
            return List.copyOf(rows);
        }

        @Override
        public void save(MessageReceiver receiver) {
            rows.add(receiver);
        }

        @Override
        public Optional<MessageReceiver> findByMessageAndPerson(String messageId, String personId) {
            return rows.stream()
                    .filter(row -> row.messageId().equals(messageId) && row.personId().equals(personId))
                    .findFirst();
        }

        @Override
        public List<MessageReceiver> pageByPerson(String personId, MessageKind kind, Boolean read, String cursor,
                                                  int size) {
            return rows.stream()
                    .filter(row -> row.personId().equals(personId))
                    .filter(row -> cursor == null || row.id().compareTo(cursor) < 0)
                    .filter(row -> kind == null || kindOf(row.messageId()) == kind)
                    .filter(row -> read == null || (read == (row.readAt() != null)))
                    .sorted(Comparator.comparing(MessageReceiver::id).reversed())
                    .limit(size)
                    .toList();
        }

        @Override
        public int markRead(String messageId, String personId, Instant readAt) {
            for (int index = 0; index < rows.size(); index++) {
                MessageReceiver row = rows.get(index);
                if (row.messageId().equals(messageId) && row.personId().equals(personId) && row.readAt() == null) {
                    rows.set(index, new MessageReceiver(row.id(), row.messageId(), row.personId(), readAt,
                            row.deliverStatus()));
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public int markAllRead(String personId, MessageKind kind, Instant readAt) {
            int affected = 0;
            for (int index = 0; index < rows.size(); index++) {
                MessageReceiver row = rows.get(index);
                if (!row.personId().equals(personId) || row.readAt() != null) {
                    continue;
                }
                if (kind != null && kindOf(row.messageId()) != kind) {
                    continue;
                }
                rows.set(index, new MessageReceiver(row.id(), row.messageId(), row.personId(), readAt,
                        row.deliverStatus()));
                affected++;
            }
            return affected;
        }

        @Override
        public Map<String, Long> unreadByKind(String personId) {
            Map<String, Long> counts = new LinkedHashMap<>();
            for (MessageReceiver row : rows) {
                if (!row.personId().equals(personId) || row.readAt() != null) {
                    continue;
                }
                counts.merge(kindOf(row.messageId()).name(), 1L, Long::sum);
            }
            return counts;
        }

        @Override
        public int deleteUnreadByMessage(String messageId) {
            int before = rows.size();
            rows.removeIf(row -> row.messageId().equals(messageId) && row.readAt() == null);
            return before - rows.size();
        }

        @Override
        public int deleteReadBefore(Instant before) {
            int previous = rows.size();
            rows.removeIf(row -> row.readAt() != null && row.readAt().isBefore(before));
            return previous - rows.size();
        }

        private MessageKind kindOf(String messageId) {
            return messages.byId.get(messageId).kind();
        }
    }

    static final class InMemoryMessageTemplateRepository implements MessageTemplateRepository {

        final Map<String, MessageTemplate> byId = new LinkedHashMap<>();

        @Override
        public Optional<MessageTemplate> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<MessageTemplate> findActive(String appCode, String code, String channel) {
            return byId.values().stream()
                    .filter(template -> Objects.equals(template.appCode(), appCode))
                    .filter(template -> template.code().equals(code) && template.channel().equals(channel))
                    .filter(template -> MessageTemplate.STATUS_ACTIVE.equals(template.status()))
                    .findFirst();
        }

        @Override
        public List<MessageTemplate> list(String appCode, String channel) {
            return byId.values().stream()
                    .filter(template -> appCode == null || Objects.equals(template.appCode(), appCode))
                    .filter(template -> channel == null || template.channel().equals(channel))
                    .toList();
        }

        @Override
        public boolean exists(String appCode, String code, String channel) {
            return byId.values().stream()
                    .anyMatch(template -> Objects.equals(template.appCode(), appCode)
                            && template.code().equals(code) && template.channel().equals(channel));
        }

        @Override
        public void save(MessageTemplate template) {
            byId.put(template.id(), template);
        }
    }

    static final class InMemoryAnnouncementRepository implements AnnouncementRepository {

        final Map<String, Announcement> byId = new LinkedHashMap<>();

        @Override
        public Optional<Announcement> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<Announcement> page(AnnouncementStatus status, AnnouncementScopeType scope, String cursor, int size) {
            return byId.values().stream()
                    .filter(announcement -> status == null || announcement.status() == status)
                    .filter(announcement -> scope == null || announcement.scopeType() == scope)
                    .filter(announcement -> cursor == null || announcement.id().compareTo(cursor) < 0)
                    .sorted(Comparator.comparing(Announcement::id).reversed())
                    .limit(size)
                    .toList();
        }

        @Override
        public void save(Announcement announcement) {
            byId.put(announcement.id(), announcement);
        }
    }

    static final class StubPersonApi implements PersonApi {

        final Set<String> allPersons = new LinkedHashSet<>();
        final Map<String, Set<String>> orgPersons = new LinkedHashMap<>();

        @Override
        public Optional<PersonView> findById(String personId) {
            return Optional.empty();
        }

        @Override
        public Optional<PersonView> findByEmpNo(String empNo) {
            return Optional.empty();
        }

        @Override
        public Optional<PersonView> findByEmail(String email) {
            return Optional.empty();
        }

        @Override
        public Set<String> personsInOrgUnit(String orgUnitId, boolean includeSubUnits) {
            return orgPersons.getOrDefault(orgUnitId, Set.of());
        }

        @Override
        public Optional<String> primaryOrgUnitId(String personId) {
            return Optional.empty();
        }

        @Override
        public Optional<String> managerId(String personId) {
            return Optional.empty();
        }

        @Override
        public Set<String> allPersonIds() {
            return Set.copyOf(allPersons);
        }
    }

    static final class StubGrantApi implements GrantApi {

        final List<RoleGrantView> grants = new ArrayList<>();

        @Override
        public List<RoleGrantView> list(String subjectType, String subjectId) {
            return grants.stream()
                    .filter(grant -> subjectType == null || grant.subjectType().equals(subjectType))
                    .filter(grant -> subjectId == null || grant.subjectId().equals(subjectId))
                    .toList();
        }

        @Override
        public String grant(RoleGrantCmd cmd) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void revoke(String grantId, String operator) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<String> effectiveRoles(String personId, String appCode, Instant at) {
            return Set.of();
        }
    }

    static final class RecordingOutbox implements OutboxAppender {

        final List<String> eventTypes = new ArrayList<>();
        final List<Map<String, Object>> payloads = new ArrayList<>();

        @Override
        public void append(String topic, String eventType, String aggregate, Map<String, Object> payload) {
            eventTypes.add(eventType);
            payloads.add(payload);
        }
    }

    static final class RecordingChannel implements NotificationChannel {

        private final String code;
        private final DataLabel clearance;
        final List<String> deliveredMessageIds = new ArrayList<>();

        RecordingChannel(String code, DataLabel clearance) {
            this.code = code;
            this.clearance = clearance;
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public DataLabel clearance() {
            return clearance;
        }

        @Override
        public int deliver(Message message, List<String> receiverIds) {
            deliveredMessageIds.add(message.id());
            return receiverIds.size();
        }
    }
}
