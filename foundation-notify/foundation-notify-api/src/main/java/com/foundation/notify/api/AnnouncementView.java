package com.foundation.notify.api;

import java.time.Instant;

/**
 * 公告视图（F-09 BAS-16 §接口）。
 *
 * @param id          公告 ID（{@code ann_…}）
 * @param title       标题
 * @param content     正文
 * @param scopeType   范围类型
 * @param scopeId     范围目标 ID，可空（{@code GLOBAL}）
 * @param status      状态
 * @param publisherId 发布人 ID
 * @param dataLabel   数据标签
 * @param publishAt   发布时间，可空
 * @param expireAt    过期时间，可空
 * @param createAt    创建时间
 */
public record AnnouncementView(String id,
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
}
