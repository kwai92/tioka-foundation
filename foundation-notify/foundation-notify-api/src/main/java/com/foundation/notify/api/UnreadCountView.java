package com.foundation.notify.api;

import java.util.Map;

/**
 * 未读数（F-09 BAS-16 §接口 {@code GET /api/fdn/messages/unread-count}）。
 *
 * @param total  未读总数
 * @param byKind 按消息类别拆分的未读数（键为 {@link MessageKind} 名称）
 */
public record UnreadCountView(long total, Map<String, Long> byKind) {
}
