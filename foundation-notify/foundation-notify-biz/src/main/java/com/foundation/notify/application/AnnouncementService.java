package com.foundation.notify.application;

import com.foundation.authz.api.GrantApi;
import com.foundation.authz.api.RoleGrantView;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.notify.api.AnnouncementApi;
import com.foundation.notify.api.AnnouncementCmd;
import com.foundation.notify.api.AnnouncementPageView;
import com.foundation.notify.api.AnnouncementScopeType;
import com.foundation.notify.api.AnnouncementStatus;
import com.foundation.notify.api.AnnouncementView;
import com.foundation.notify.api.MessageKind;
import com.foundation.notify.api.MessagePriority;
import com.foundation.notify.domain.Announcement;
import com.foundation.notify.domain.Message;
import com.foundation.notify.domain.MessageReceiver;
import com.foundation.notify.infrastructure.AnnouncementEventPublisher;
import com.foundation.notify.infrastructure.AnnouncementRepository;
import com.foundation.notify.infrastructure.MessageReceiverRepository;
import com.foundation.notify.infrastructure.MessageRepository;
import com.foundation.org.api.PersonApi;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 公告应用服务（F-09 BAS-16 §接口 {@code /api/fdn/announcements}）。
 *
 * <p>发布按范围展开收件人并生成一条 {@code ANNOUNCEMENT} 消息；离线撤回其未读可见性（已读保留）。
 */
public class AnnouncementService implements AnnouncementApi {

    private final AnnouncementRepository announcements;
    private final MessageRepository messages;
    private final MessageReceiverRepository receivers;
    private final PersonApi persons;
    private final GrantApi grants;
    private final AnnouncementEventPublisher announcementEvents;
    private final UnreadCounter unreadCounter;

    public AnnouncementService(AnnouncementRepository announcements, MessageRepository messages,
                               MessageReceiverRepository receivers, PersonApi persons, GrantApi grants,
                               AnnouncementEventPublisher announcementEvents,
                               UnreadCounter unreadCounter) {
        this.announcements = announcements;
        this.messages = messages;
        this.receivers = receivers;
        this.persons = persons;
        this.grants = grants;
        this.announcementEvents = announcementEvents;
        this.unreadCounter = unreadCounter;
    }

    @Override
    public AnnouncementPageView list(String status, String scope, String cursor, int size) {
        int pageSize = size <= 0 ? 20 : Math.min(size, 100);
        List<Announcement> rows = announcements.page(AnnouncementStatus.of(status),
                AnnouncementScopeType.of(scope), cursor, pageSize);
        if (rows.isEmpty()) {
            return new AnnouncementPageView(List.of(), null);
        }
        List<AnnouncementView> items = rows.stream().map(AnnouncementService::toView).toList();
        String nextCursor = rows.size() == pageSize ? rows.get(rows.size() - 1).id() : null;
        return new AnnouncementPageView(items, nextCursor);
    }

    @Override
    @Transactional
    public AnnouncementView create(AnnouncementCmd cmd, String publisherId) {
        Announcement announcement = Announcement.draft(cmd.title(), cmd.content(), cmd.scopeType(), cmd.scopeId(),
                publisherId, cmd.dataLabel(), cmd.publishAt(), cmd.expireAt());
        announcements.save(announcement);
        return toView(announcement);
    }

    @Override
    @Transactional
    public AnnouncementView update(String id, AnnouncementCmd cmd) {
        Announcement existing = announcements.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "公告不存在：" + id));
        if (existing.status() != AnnouncementStatus.DRAFT) {
            throw new BizException(ErrorCode.CONFLICT, "仅草稿可修改：" + id);
        }
        // 保持 id/status/publisherId/createAt，仅覆盖可编辑字段
        Announcement updated = new Announcement(existing.id(),
                cmd.title() != null ? cmd.title() : existing.title(),
                cmd.content() != null ? cmd.content() : existing.content(),
                cmd.scopeType() != null ? cmd.scopeType() : existing.scopeType(),
                cmd.scopeId() != null ? cmd.scopeId() : existing.scopeId(),
                existing.status(),
                existing.publisherId(),
                cmd.dataLabel() != null ? cmd.dataLabel() : existing.dataLabel(),
                cmd.publishAt() != null ? cmd.publishAt() : existing.publishAt(),
                cmd.expireAt() != null ? cmd.expireAt() : existing.expireAt(),
                existing.createAt());
        announcements.save(updated);
        return toView(updated);
    }

    @Override
    @Transactional
    public AnnouncementView publish(String id) {
        Announcement announcement = announcements.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "公告不存在：" + id));
        if (announcement.status() != AnnouncementStatus.DRAFT) {
            throw new BizException(ErrorCode.CONFLICT, "仅草稿可发布：" + id);
        }
        Set<String> targets = expand(announcement.scopeType(), announcement.scopeId());

        Message message = Message.create("fdn", MessageKind.ANNOUNCEMENT, announcement.title(),
                announcement.content(), null, Map.of(), null, MessagePriority.NORMAL,
                announcement.publisherId(), null, List.of("INBOX"),
                "announcement:" + announcement.id(), announcement.dataLabel());
        messages.save(message);
        for (String personId : targets) {
            receivers.save(MessageReceiver.sent(message.id(), personId));
        }

        // 即使范围内无收件人也置为已发布（0 收件人）
        Announcement saved = announcement.withStatus(AnnouncementStatus.PUBLISHED).withPublishAt(Instant.now());
        announcements.save(saved);
        announcementEvents.publish(saved, targets.size());
        for (String personId : targets) {
            unreadCounter.invalidate(personId);
        }
        return toView(saved);
    }

    @Override
    @Transactional
    public AnnouncementView offline(String id) {
        Announcement announcement = announcements.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "公告不存在：" + id));
        Announcement saved = announcement.withStatus(AnnouncementStatus.OFFLINE);
        announcements.save(saved);
        // 撤回展开消息的未读可见性：删除未读接收行，已读保留
        messages.findByDedupeKey("fdn", "announcement:" + id)
                .ifPresent(message -> receivers.deleteUnreadByMessage(message.id()));
        return toView(saved);
    }

    /** 按范围展开收件人人员 ID 集合。 */
    private Set<String> expand(AnnouncementScopeType scopeType, String scopeId) {
        Set<String> targets = new LinkedHashSet<>();
        if (scopeType == AnnouncementScopeType.GLOBAL) {
            targets.addAll(persons.allPersonIds());
        } else if (scopeType == AnnouncementScopeType.ORG) {
            targets.addAll(persons.personsInOrgUnit(scopeId, true));
        } else if (scopeType == AnnouncementScopeType.ROLE) {
            for (RoleGrantView grant : grants.list(null, null)) {
                if (!scopeId.equals(grant.roleCode())) {
                    continue;
                }
                if ("PERSON".equals(grant.subjectType())) {
                    targets.add(grant.subjectId());
                } else if ("ORG_UNIT".equals(grant.subjectType())) {
                    targets.addAll(persons.personsInOrgUnit(grant.subjectId(), grant.includeDescendants()));
                }
                // POST（岗位）等主体暂无法解析为人员：BAS-06 组织归属解析提供后再补
            }
        }
        return targets;
    }

    private static AnnouncementView toView(Announcement announcement) {
        return new AnnouncementView(announcement.id(), announcement.title(), announcement.content(),
                announcement.scopeType(), announcement.scopeId(), announcement.status(),
                announcement.publisherId(), announcement.dataLabel(), announcement.publishAt(),
                announcement.expireAt(), announcement.createAt());
    }
}
