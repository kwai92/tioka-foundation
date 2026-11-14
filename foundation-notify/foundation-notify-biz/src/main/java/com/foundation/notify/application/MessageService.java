package com.foundation.notify.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.notify.api.MessageApi;
import com.foundation.notify.api.MessageKind;
import com.foundation.notify.api.MessagePageView;
import com.foundation.notify.api.MessageQuery;
import com.foundation.notify.api.MessageSendCmd;
import com.foundation.notify.api.MessageView;
import com.foundation.notify.api.UnreadCountView;
import com.foundation.notify.config.NotifyProperties;
import com.foundation.notify.domain.DataLabel;
import com.foundation.notify.domain.Message;
import com.foundation.notify.domain.MessageReceiver;
import com.foundation.notify.domain.MessageTemplate;
import com.foundation.notify.infrastructure.MessageEventPublisher;
import com.foundation.notify.infrastructure.MessageReceiverRepository;
import com.foundation.notify.infrastructure.MessageRepository;
import com.foundation.notify.infrastructure.MessageTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 消息中心应用服务（F-09 BAS-16 §接口 {@code /api/fdn/messages}）。
 *
 * <p>负责投放与去重、渠道渲染与过滤、收件行与未读数维护；公告展开见 {@link AnnouncementService}。
 */
public class MessageService implements MessageApi {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final MessageRepository messages;
    private final MessageReceiverRepository receivers;
    private final MessageTemplateRepository templates;
    private final Map<String, NotificationChannel> channelMap;
    private final UnreadCounter unreadCounter;
    private final MessageEventPublisher events;
    private final NotifyProperties properties;

    public MessageService(MessageRepository messages, MessageReceiverRepository receivers,
                          MessageTemplateRepository templates, List<NotificationChannel> channels,
                          UnreadCounter unreadCounter, MessageEventPublisher events, NotifyProperties properties) {
        this.messages = messages;
        this.receivers = receivers;
        this.templates = templates;
        this.channelMap = channels.stream()
                .collect(Collectors.toMap(NotificationChannel::code, Function.identity(), (a, b) -> a));
        this.unreadCounter = unreadCounter;
        this.events = events;
        this.properties = properties;
    }

    @Override
    @Transactional
    public MessageView send(MessageSendCmd cmd, String senderId, String senderName) {
        if (cmd.appCode() == null || cmd.appCode().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "appCode 不能为空");
        }
        if (cmd.kind() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "消息类别不能为空");
        }
        if (cmd.kind() == MessageKind.ANNOUNCEMENT) {
            throw new BizException(ErrorCode.BAD_REQUEST, "公告只能经公告接口发布展开");
        }
        if (cmd.receivers() == null || cmd.receivers().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "收件人不能为空");
        }

        // 幂等：同 (appCode, dedupeKey) 重复投放直接返回原消息
        if (cmd.dedupeKey() != null && !cmd.dedupeKey().isBlank()) {
            Optional<Message> existing = messages.findByDedupeKey(cmd.appCode(), cmd.dedupeKey());
            if (existing.isPresent()) {
                return toView(existing.get(), null);
            }
        }

        List<String> requested = cmd.channels() != null && !cmd.channels().isEmpty()
                ? cmd.channels()
                : properties.getChannels();

        String title = cmd.title();
        String body = cmd.body();
        if (cmd.templateCode() != null && !cmd.templateCode().isBlank()
                && requested != null && !requested.isEmpty()) {
            Optional<MessageTemplate> template =
                    templates.findActive(cmd.appCode(), cmd.templateCode(), requested.get(0));
            if (template.isPresent()) {
                title = render(template.get().titleTpl(), cmd.payload());
                body = render(template.get().bodyTpl(), cmd.payload());
            }
        }
        if (title == null || title.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "标题不能为空");
        }

        // 数据标签过滤：渠道去向量许可必须不低于消息密级（ADR-040）
        DataLabel label = DataLabel.of(cmd.dataLabel());
        List<String> effectiveChannels = new ArrayList<>();
        if (requested != null) {
            for (String code : requested) {
                NotificationChannel channel = channelMap.get(code);
                if (channel != null && label.within(channel.clearance())) {
                    effectiveChannels.add(code);
                }
            }
        }

        Message message = Message.create(cmd.appCode(), cmd.kind(), title, body, cmd.templateCode(),
                cmd.payload(), cmd.link(), cmd.priority(), senderId, senderName, effectiveChannels,
                cmd.dedupeKey(), label.name());
        messages.save(message);

        List<String> receiverIds = cmd.receivers().stream().filter(id -> id != null && !id.isBlank())
                .distinct().toList();
        for (String personId : receiverIds) {
            receivers.save(MessageReceiver.sent(message.id(), personId));
        }

        for (String code : effectiveChannels) {
            if ("INBOX".equals(code)) {
                continue;
            }
            NotificationChannel channel = channelMap.get(code);
            if (channel == null) {
                continue;
            }
            try {
                channel.deliver(message, receiverIds);
            } catch (RuntimeException ex) {
                log.warn("渠道 {} 投递失败：消息 {}", code, message.id(), ex);
            }
        }

        events.publishSent(message, effectiveChannels, receiverIds.size());
        for (String personId : receiverIds) {
            unreadCounter.invalidate(personId);
        }
        return toView(message, null);
    }

    @Override
    public MessagePageView mine(String personId, MessageQuery query) {
        int size = clampSize(query.size());
        List<MessageReceiver> rows = receivers.pageByPerson(personId, query.kind(), query.read(),
                query.cursor(), size);
        if (rows.isEmpty()) {
            return new MessagePageView(List.of(), null);
        }
        List<String> ids = rows.stream().map(MessageReceiver::messageId).toList();
        Map<String, Message> messageById = messages.findByIds(ids).stream()
                .collect(Collectors.toMap(Message::id, Function.identity(), (a, b) -> a));
        List<MessageView> items = new ArrayList<>();
        for (MessageReceiver row : rows) {
            Message message = messageById.get(row.messageId());
            if (message != null) {
                items.add(toView(message, row.readAt()));
            }
        }
        String nextCursor = rows.size() == size ? rows.get(rows.size() - 1).id() : null;
        return new MessagePageView(items, nextCursor);
    }

    @Override
    @Transactional
    public MessageView markRead(String personId, String messageId) {
        Message message = messages.findById(messageId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "消息不存在：" + messageId));
        MessageReceiver receiver = receivers.findByMessageAndPerson(messageId, personId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "消息未投递到当前用户：" + messageId));
        Instant readAt = receiver.readAt() != null ? receiver.readAt() : Instant.now();
        receivers.markRead(messageId, personId, readAt);
        unreadCounter.invalidate(personId);
        return toView(message, readAt);
    }

    @Override
    @Transactional
    public int markAllRead(String personId, MessageKind kind) {
        int affected = receivers.markAllRead(personId, kind, Instant.now());
        if (affected != 0) {
            unreadCounter.invalidate(personId);
        }
        return affected;
    }

    @Override
    public UnreadCountView unreadCount(String personId) {
        return unreadCounter.count(personId);
    }

    /** 保留期清理：删除超过保留期的已读接收行（供定时任务调用）。 */
    public int purgeReadBefore(int retentionDays) {
        return receivers.deleteReadBefore(Instant.now().minus(Duration.ofDays(retentionDays)));
    }

    /** 模板渲染：将 {@code {{key}}} 占位替换为 payload 中同名 key 的字符串值（简化实现）。 */
    private static String render(String template, Map<String, Object> payload) {
        if (template == null || payload == null || payload.isEmpty()) {
            return template;
        }
        String rendered = template;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return rendered;
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private static MessageView toView(Message message, Instant readAt) {
        return new MessageView(message.id(), message.appCode(), message.kind(), message.title(), message.body(),
                message.templateCode(), message.payload(), message.link(), message.priority(), message.senderId(),
                message.senderName(), message.channels(), message.dataLabel(), message.createAt(),
                readAt != null, readAt);
    }
}
