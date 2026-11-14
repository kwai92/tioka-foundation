package com.foundation.notify.api;

/**
 * 公告契约（F-09 BAS-16 §接口）：草稿、发布与下线；发布按范围展开收件人。
 *
 * <p>权限码 {@code fdn:announcement:publish}。
 */
public interface AnnouncementApi {

    /** 公告列表（按状态 / 范围过滤，游标分页）。 */
    AnnouncementPageView list(String status, String scope, String cursor, int size);

    /** 新建公告草稿。 */
    AnnouncementView create(AnnouncementCmd cmd, String publisherId);

    /** 更新公告草稿（已发布 / 已下线不可改）。 */
    AnnouncementView update(String id, AnnouncementCmd cmd);

    /** 发布：按范围展开为 {@code kind=ANNOUNCEMENT} 的消息并生成收件人，发 {@code fdn.announcement.published}。 */
    AnnouncementView publish(String id);

    /** 下线：撤回展开消息的未读可见性（已读保留）。 */
    AnnouncementView offline(String id);
}
