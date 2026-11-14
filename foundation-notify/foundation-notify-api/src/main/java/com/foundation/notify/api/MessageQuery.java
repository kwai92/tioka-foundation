package com.foundation.notify.api;

/**
 * 「我的消息」查询条件（F-09 BAS-16 §接口 {@code GET /api/fdn/messages/mine}）。
 *
 * @param kind   消息类别，{@code null} 表示不限
 * @param read   已读过滤，{@code null} 表示不限
 * @param cursor 游标（上一页 {@code nextCursor}），空表示首页
 * @param size   页大小（服务端收敛到 1..100）
 */
public record MessageQuery(MessageKind kind, Boolean read, String cursor, int size) {
}
