package com.foundation.notify.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.notify.api.UnreadCountView;
import com.foundation.notify.infrastructure.MessageReceiverRepository;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * 未读数热点缓存（F-09 BAS-16）：key {@code fdn:msg:unread:{personId}}，TTL 10 分钟。
 *
 * <p>Redis 可空（无 Redis 的上下文降级为每次走 DB）；缓存命中失败一律忽略并回源，
 * 避免读路径被缓存抖动击穿。
 */
public class UnreadCounter {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "fdn:msg:unread:";

    private final MessageReceiverRepository receivers;
    private final StringRedisTemplate redis;

    public UnreadCounter(MessageReceiverRepository receivers, StringRedisTemplate redis) {
        this.receivers = receivers;
        this.redis = redis;
    }

    /** 取未读数；命中缓存直接返回，否则回源并回填。 */
    public UnreadCountView count(String personId) {
        String key = KEY_PREFIX + personId;
        if (redis != null) {
            UnreadCountView cached = readCache(key);
            if (cached != null) {
                return cached;
            }
        }
        Map<String, Long> byKind = receivers.unreadByKind(personId);
        long total = byKind.values().stream().mapToLong(Long::longValue).sum();
        UnreadCountView view = new UnreadCountView(total, byKind);
        writeCache(key, view);
        return view;
    }

    /** 未读状态变更后失效缓存。 */
    public void invalidate(String personId) {
        if (redis != null) {
            redis.delete(KEY_PREFIX + personId);
        }
    }

    private UnreadCountView readCache(String key) {
        try {
            String cached = redis.opsForValue().get(key);
            if (cached == null || cached.isBlank()) {
                return null;
            }
            return JSON.readValue(cached, UnreadCountView.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private void writeCache(String key, UnreadCountView view) {
        if (redis == null) {
            return;
        }
        try {
            redis.opsForValue().set(key, JSON.writeValueAsString(view), TTL);
        } catch (Exception ex) {
            // 缓存写入失败不影响主流程
        }
    }
}
