package com.foundation.notify.api;

import java.time.Instant;

/**
 * 公告草稿 / 更新命令（F-09 BAS-16 §接口 {@code POST/PUT /api/fdn/announcements}）。
 *
 * <p>权限码 {@code fdn:announcement:publish}。
 *
 * @param title     标题
 * @param content   正文
 * @param scopeType 范围类型
 * @param scopeId   范围目标 ID（{@link AnnouncementScopeType#ORG}/{@link AnnouncementScopeType#ROLE} 必填）
 * @param dataLabel 数据标签；空取 {@code INTERNAL}
 * @param publishAt 计划发布时间，可空（发布时取当前时间）
 * @param expireAt  过期时间，可空
 */
public record AnnouncementCmd(String title,
                              String content,
                              AnnouncementScopeType scopeType,
                              String scopeId,
                              String dataLabel,
                              Instant publishAt,
                              Instant expireAt) {
}
