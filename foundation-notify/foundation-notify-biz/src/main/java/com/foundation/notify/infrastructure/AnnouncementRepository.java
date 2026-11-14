package com.foundation.notify.infrastructure;

import com.foundation.notify.api.AnnouncementScopeType;
import com.foundation.notify.api.AnnouncementStatus;
import com.foundation.notify.domain.Announcement;

import java.util.List;
import java.util.Optional;

/** 公告头仓储：游标分页按主键倒序。 */
public interface AnnouncementRepository {

    Optional<Announcement> findById(String id);

    List<Announcement> page(AnnouncementStatus status, AnnouncementScopeType scope, String cursor, int size);

    void save(Announcement announcement);
}
