package com.foundation.notify.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import com.foundation.notify.api.MessageKind;
import com.foundation.notify.api.MessagePriority;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 消息主体（F-09 BAS-16 §数据模型 {@code fdn_message}）。
 *
 * <p>公告发布时按范围展开为一条 {@link MessageKind#ANNOUNCEMENT}，与站内信共用本模型。
 */
public record Message(String id,
                      String appCode,
                      MessageKind kind,
                      String title,
                      String body,
                      String templateCode,
                      Map<String, Object> payload,
                      String link,
                      MessagePriority priority,
                      String senderId,
                      String senderName,
                      List<String> channels,
                      String dedupeKey,
                      String dataLabel,
                      Instant createAt) {

    /** 新建消息：生成 ID，补齐默认值并校验必填项。 */
    public static Message create(String appCode, MessageKind kind, String title, String body, String templateCode,
                                 Map<String, Object> payload, String link, MessagePriority priority,
                                 String senderId, String senderName, List<String> channels, String dedupeKey,
                                 String dataLabel) {
        if (appCode == null || appCode.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "appCode 不能为空");
        }
        if (kind == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "消息类别不能为空");
        }
        if (title == null || title.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "标题不能为空");
        }
        return new Message(
                Ids.newId(NotifyIdPrefix.MESSAGE),
                appCode,
                kind,
                title,
                body,
                templateCode,
                payload == null ? Map.of() : payload,
                link,
                priority == null ? MessagePriority.NORMAL : priority,
                senderId,
                senderName,
                channels == null || channels.isEmpty() ? List.of("INBOX") : channels,
                dedupeKey,
                dataLabel == null || dataLabel.isBlank() ? "INTERNAL" : dataLabel,
                Instant.now());
    }
}
