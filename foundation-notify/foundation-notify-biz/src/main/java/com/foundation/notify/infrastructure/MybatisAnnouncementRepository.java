package com.foundation.notify.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.notify.api.AnnouncementScopeType;
import com.foundation.notify.api.AnnouncementStatus;
import com.foundation.notify.domain.Announcement;
import com.foundation.notify.infrastructure.mapper.AnnouncementMapper;
import com.foundation.notify.infrastructure.persistence.AnnouncementDO;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 公告头仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisAnnouncementRepository implements AnnouncementRepository {

    private final AnnouncementMapper mapper;

    public MybatisAnnouncementRepository(AnnouncementMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Announcement> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisAnnouncementRepository::toDomain);
    }

    @Override
    public List<Announcement> page(AnnouncementStatus status, AnnouncementScopeType scope, String cursor, int size) {
        int limit = Math.max(1, Math.min(size, 100));
        return mapper.selectList(Wrappers.<AnnouncementDO>lambdaQuery()
                        .eq(status != null, AnnouncementDO::getStatus, status == null ? null : status.name())
                        .eq(scope != null, AnnouncementDO::getScopeType, scope == null ? null : scope.name())
                        .lt(cursor != null && !cursor.isBlank(), AnnouncementDO::getId, cursor)
                        .orderByDesc(AnnouncementDO::getId)
                        .last("LIMIT " + limit))
                .stream().map(MybatisAnnouncementRepository::toDomain).toList();
    }

    @Override
    public void save(Announcement announcement) {
        AnnouncementDO row = new AnnouncementDO();
        row.setId(announcement.id());
        row.setTitle(announcement.title());
        row.setContent(announcement.content());
        row.setScopeType(announcement.scopeType().name());
        row.setScopeId(announcement.scopeId());
        row.setStatus(announcement.status().name());
        row.setPublisherId(announcement.publisherId());
        row.setDataLabel(announcement.dataLabel());
        row.setPublishAt(toLocal(announcement.publishAt()));
        row.setExpireAt(toLocal(announcement.expireAt()));

        if (mapper.selectById(announcement.id()) == null) {
            mapper.insert(row);
        } else {
            mapper.updateById(row);
        }
    }

    private static LocalDateTime toLocal(java.time.Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Announcement toDomain(AnnouncementDO row) {
        return new Announcement(row.getId(), row.getTitle(), row.getContent(),
                AnnouncementScopeType.of(row.getScopeType()), row.getScopeId(),
                AnnouncementStatus.of(row.getStatus()), row.getPublisherId(), row.getDataLabel(),
                row.getPublishAt() == null ? null : row.getPublishAt().toInstant(ZoneOffset.UTC),
                row.getExpireAt() == null ? null : row.getExpireAt().toInstant(ZoneOffset.UTC),
                row.getCreateAt() == null ? null : row.getCreateAt().toInstant(ZoneOffset.UTC));
    }
}
