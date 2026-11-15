package com.foundation.starter.client.cache;

import com.foundation.starter.client.config.DegradePolicy;
import com.foundation.starter.client.http.FoundationUnavailableException;
import com.foundation.starter.core.context.DegradeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ADP-08：只读缓存的默认拒绝、过期兜底、陈旧上限与降级标记。 */
class TtlCacheDegradeTest {

    private final MutableClock clock = new MutableClock();

    @AfterEach
    void cleanUp() {
        DegradeContext.clear();
    }

    @Test
    void 默认失败关闭并抛出明确异常() {
        TtlCache<String, String> cache = new TtlCache<>(1_000, clock);
        cache.getWithDegrade("fdn:dict:a", () -> "v1");
        clock.advance(Duration.ofMillis(1_001));

        FoundationUnavailableException ex = assertThrows(FoundationUnavailableException.class,
                () -> cache.getWithDegrade("fdn:dict:a", () -> { throw new IllegalStateException("down"); }));
        assertEquals("fdn:dict:a", ex.getResource());
        assertFalse(DegradeContext.isMarked());
    }

    @Test
    void 配置过期兜底时返回旧值并打降级标记() {
        AtomicLong observedStale = new AtomicLong(-1);
        TtlCache<String, String> cache = new TtlCache<>(1_000, clock,
                DegradePolicy.STALE_CACHE, 5_000,
                (key, staleMillis) -> observedStale.set(staleMillis));
        cache.getWithDegrade("fdn:param:a", () -> "v1");
        clock.advance(Duration.ofMillis(2_000)); // 已过期 1000ms

        assertEquals("v1", cache.getWithDegrade("fdn:param:a",
                () -> { throw new IllegalStateException("foundation down"); }));
        assertEquals(1_000, observedStale.get());
        assertTrue(DegradeContext.isMarked(), "降级必须打标记以便响应头透传");
    }

    @Test
    void 超出最大陈旧时长仍拒绝() {
        TtlCache<String, String> cache = new TtlCache<>(1_000, clock,
                DegradePolicy.STALE_CACHE, 5_000);
        cache.getWithDegrade("k", () -> "v1");
        clock.advance(Duration.ofMillis(7_000)); // 过期 6000ms > 上限

        assertThrows(FoundationUnavailableException.class,
                () -> cache.getWithDegrade("k", () -> { throw new IllegalStateException("down"); }));
        assertFalse(DegradeContext.isMarked());
    }

    @Test
    void 无缓存时即使开启兜底也拒绝() {
        TtlCache<String, String> cache = new TtlCache<>(1_000, clock,
                DegradePolicy.STALE_CACHE, 60_000);
        assertThrows(FoundationUnavailableException.class,
                () -> cache.getWithDegrade("k", () -> { throw new IllegalStateException("down"); }));
    }

    @Test
    void 未过期缓存命中不回源也不标记() {
        TtlCache<String, String> cache = new TtlCache<>(10_000, clock,
                DegradePolicy.STALE_CACHE, 60_000);
        cache.getWithDegrade("k", () -> "v1");
        assertEquals("v1", cache.getWithDegrade("k", () -> { throw new AssertionError("不应回源"); }));
        assertFalse(DegradeContext.isMarked());
    }

    @Test
    void 回源返回空结果保持业务语义不触发降级() {
        TtlCache<String, String> cache = new TtlCache<>(1_000, clock,
                DegradePolicy.STALE_CACHE, 60_000);
        assertNull(cache.getWithDegrade("k", () -> null));
        assertFalse(DegradeContext.isMarked());
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
