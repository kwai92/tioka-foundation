package com.foundation.notify.api;

import com.foundation.notify.api.MessageApi;
import com.foundation.notify.api.MessageKind;
import com.foundation.notify.api.MessagePageView;
import com.foundation.notify.api.MessagePriority;
import com.foundation.notify.api.MessageQuery;
import com.foundation.notify.api.MessageSendCmd;
import com.foundation.notify.api.MessageView;
import com.foundation.notify.api.UnreadCountView;
import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

/**
 * 消息 REST（F-09 BAS-16 §接口 {@code /api/fdn/messages}）：发送、我的消息、已读与未读数。
 *
 * <p>本类不标 {@code @RestController}：由 {@code NotifyAutoConfiguration} 以 {@code @Bean} 装配，
 * 与 {@code MessageApi} 同生命周期（无持久化上下文时不注册）。
 */
@ResponseBody
@RequestMapping("/api/fdn/messages")
public class MessageController {

    private final MessageApi messages;

    public MessageController(MessageApi messages) {
        this.messages = messages;
    }

    /** 发送站内信 / 通知、登记待办；按 {@code (appCode, dedupeKey)} 幂等。 */
    @PostMapping
    @RequirePermission("fdn:notify:send")
    public MessageView send(@RequestBody SendRequest request) {
        String[] operator = PrincipalHolder.current()
                .map(p -> new String[]{p.userId(), p.displayName()})
                .orElse(new String[]{"system", null});
        MessageSendCmd cmd = new MessageSendCmd(request.appCode(), MessageKind.of(request.kind()),
                request.templateCode(), request.title(), request.body(), request.payload(), request.link(),
                request.channels(), request.receivers(), request.dedupeKey(),
                MessagePriority.of(request.priority()), request.dataLabel());
        return messages.send(cmd, operator[0], operator[1]);
    }

    /** 我的消息（游标分页）。 */
    @GetMapping("/mine")
    public MessagePageView mine(@RequestParam(required = false) String kind,
                                @RequestParam(required = false) Boolean read,
                                @RequestParam(required = false) String cursor,
                                @RequestParam(defaultValue = "20") int size) {
        String personId = PrincipalHolder.require().personId();
        return messages.mine(personId, new MessageQuery(MessageKind.of(kind), read, cursor, size));
    }

    /** 标记已读（幂等）。 */
    @PutMapping("/{id}/read")
    public MessageView markRead(@PathVariable String id) {
        String personId = PrincipalHolder.require().personId();
        return messages.markRead(personId, id);
    }

    /** 全部标记已读；{@code kind} 为空表示全部类别。 */
    @PutMapping("/read-all")
    public int markAllRead(@RequestParam(required = false) String kind) {
        String personId = PrincipalHolder.require().personId();
        return messages.markAllRead(personId, MessageKind.of(kind));
    }

    /** 未读数（{@code {total, byKind}}）。 */
    @GetMapping("/unread-count")
    public UnreadCountView unreadCount() {
        String personId = PrincipalHolder.require().personId();
        return messages.unreadCount(personId);
    }

    /** 发送请求体。 */
    public record SendRequest(String appCode,
                              String kind,
                              String templateCode,
                              String title,
                              String body,
                              Map<String, Object> payload,
                              String link,
                              List<String> channels,
                              List<String> receivers,
                              String dedupeKey,
                              String priority,
                              String dataLabel) {
    }
}
