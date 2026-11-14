package com.foundation.notify.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import com.foundation.notify.api.AnnouncementScopeType;
import com.foundation.notify.api.AnnouncementStatus;

import java.time.Instant;

/**
 * 公告头（F-09 BAS-16 §数据模型 {@code fdn_announcement}）：草稿 → 发布 → 下线。
 *
 * <p>发布时按范围展开为一条 {@code ANNOUNCEMENT} 消息，本记录只存公告头与展开参数。
 */
public record Announcement(String id,
                           String title,
                           String content,
                           AnnouncementScopeType scopeType,
                           String scopeId,
                           AnnouncementStatus status,
                           String publisherId,
                           String dataLabel,
                           Instant publishAt,
                           Instant expireAt,
                           Instant createAt) {

    /** 新建草稿：生成 ID，状态置 {@code DRAFT}；{@code ORG}/{@code ROLE} 范围必须带 {@code scopeId}。 */
    public static Announcement draft(String title, String content, AnnouncementScopeType scopeType,
                                     String scopeId, String publisherId, String dataLabel, Instant publishAt,
                                     Instant expireAt) {
        if (title == null || title.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "标题不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "正文不能为空");
        }
        if (scopeType == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "范围类型不能为空");
        }
        if ((scopeType == AnnouncementScopeType.ORG || scopeType == AnnouncementScopeType.ROLE)
                && (scopeId == null || scopeId.isBlank())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "范围 " + scopeType + " 必须指定 scopeId");
        }
        return new Announcement(
                Ids.newId(NotifyIdPrefix.ANNOUNCEMENT),
                title,
                content,
                scopeType,
                scopeId,
                AnnouncementStatus.DRAFT,
                publisherId,
                dataLabel == null || dataLabel.isBlank() ? "INTERNAL" : dataLabel,
                publishAt,
                expireAt,
                Instant.now());
    }

    /** 状态迁移：保持其余字段不变。 */
    public Announcement withStatus(AnnouncementStatus newStatus) {
        return new Announcement(id, title, content, scopeType, scopeId, newStatus, publisherId, dataLabel,
                publishAt, expireAt, createAt);
    }

    /** 设置发布时间：保持其余字段不变。 */
    public Announcement withPublishAt(Instant newPublishAt) {
        return new Announcement(id, title, content, scopeType, scopeId, status, publisherId, dataLabel,
                newPublishAt, expireAt, createAt);
    }
}
