package com.foundation.starter.client.authz;

import com.foundation.starter.client.config.DegradePolicy;
import com.foundation.starter.core.context.DegradeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P1 运行平面：版本化缓存的命中、失效、陈旧拒绝与版本不回退。 */
class AuthorizationSnapshotCacheTest {

    private static final String APP = "tioka-ai";
    private static final String PERSON = "person-001";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-16T00:00:00Z"));

    /** DegradeContext 是 ThreadLocal，用例间必须清理，避免降级标记串到别的断言。 */
    @BeforeEach
    void clearDegrade() {
        DegradeContext.clear();
    }

    @AfterEach
    void clearDegradeAfter() {
        DegradeContext.clear();
    }

    private AuthorizationSnapshotCache cache() {
        return new AuthorizationSnapshotCache(APP, Duration.ofMinutes(5), clock);
    }

    private static AuthorizationSnapshot view(String person, long version, String... codes) {
        return new AuthorizationSnapshot(APP, person, version, Set.of(codes));
    }

    @Test
    void 命中新鲜快照时零回源() {
        AuthorizationSnapshotCache cache = cache();
        AtomicInteger loads = new AtomicInteger();

        AuthorizationSnapshot first = cache.get(PERSON, () -> {
            loads.incrementAndGet();
            return view(PERSON, 7, "agent:read");
        });
        AuthorizationSnapshot second = cache.get(PERSON, () -> {
            loads.incrementAndGet();
            return view(PERSON, 8, "agent:write");
        });

        assertEquals(Set.of("agent:read"), first.permissions());
        assertEquals(7, second.version(), "TTL 内复用同一快照，不回源");
        assertEquals(1, loads.get());
        assertTrue(cache.isLive(PERSON));
        assertEquals(Long.valueOf(7), cache.cachedVersion(PERSON));
    }

    @Test
    void TTL到期后回源到新版本() {
        AuthorizationSnapshotCache cache = cache();
        cache.get(PERSON, () -> view(PERSON, 7, "agent:read"));

        clock.advance(Duration.ofMinutes(5));
        assertFalse(cache.isLive(PERSON));

        AuthorizationSnapshot refreshed = cache.get(PERSON, () -> view(PERSON, 8, "agent:write"));
        assertEquals(8, refreshed.version());
        assertEquals(Set.of("agent:write"), refreshed.permissions());
    }

    @Test
    void 到期且回源返回null时拒绝并清除陈旧条目() {
        AuthorizationSnapshotCache cache = cache();
        cache.get(PERSON, () -> view(PERSON, 7, "agent:read"));
        clock.advance(Duration.ofMinutes(5));

        assertNull(cache.get(PERSON, () -> null), "绝不能把过期快照当有效授权使用");
        assertFalse(cache.isLive(PERSON));
        assertEquals(0, cache.size(), "陈旧条目必须删除，不留可被误用的旧权限");
    }

    @Test
    void 到期且回源抛异常时拒绝() {
        AuthorizationSnapshotCache cache = cache();
        cache.get(PERSON, () -> view(PERSON, 7, "agent:read"));
        clock.advance(Duration.ofMinutes(5));

        assertNull(cache.get(PERSON, () -> {
            throw new IllegalStateException("foundation unavailable");
        }));
        assertEquals(0, cache.size());
    }

    @Test
    void 回源版本回退时拒绝放行() {
        AuthorizationSnapshotCache cache = cache();
        cache.get(PERSON, () -> view(PERSON, 7, "agent:read"));
        clock.advance(Duration.ofMinutes(5));

        assertNull(cache.get(PERSON, () -> view(PERSON, 6, "agent:read")));
        assertEquals(0, cache.size());
    }

    @Test
    void 回源身份与应用不一致时拒绝() {
        AuthorizationSnapshotCache cache = cache();

        assertNull(cache.get(PERSON, () -> view("someone-else", 7, "agent:read")));
        assertNull(cache.get(PERSON,
                () -> new AuthorizationSnapshot("other-app", PERSON, 7, Set.of("agent:read"))));
        assertEquals(0, cache.size());
    }

    @Test
    void 事件失效后下次取用回源_全量失效同样生效() {
        AuthorizationSnapshotCache cache = cache();
        AtomicInteger loads = new AtomicInteger();
        cache.get(PERSON, () -> view(PERSON, 7, "agent:read"));

        cache.invalidate(PERSON);
        assertFalse(cache.isLive(PERSON));
        assertEquals(8, cache.get(PERSON, () -> {
            loads.incrementAndGet();
            return view(PERSON, 8, "agent:write");
        }).version());
        assertEquals(1, loads.get());

        cache.get(PERSON, () -> view(PERSON, 9, "agent:read"));
        cache.invalidate(null);
        assertEquals(0, cache.size(), "personId 为 null 表示全量失效");
    }

    @Test
    void TTL为零时每次都回源_可用于排障() {
        AuthorizationSnapshotCache cache = new AuthorizationSnapshotCache(APP, Duration.ZERO, clock);
        AtomicInteger loads = new AtomicInteger();

        cache.get(PERSON, () -> {
            loads.incrementAndGet();
            return view(PERSON, 7, "agent:read");
        });
        cache.get(PERSON, () -> {
            loads.incrementAndGet();
            return view(PERSON, 7, "agent:read");
        });

        assertEquals(2, loads.get());
    }

    @Test
    void 空白人员标识与非法参数被拒绝() {
        AuthorizationSnapshotCache cache = cache();
        assertNull(cache.get("  ", () -> view(PERSON, 7, "agent:read")));
        assertNull(cache.get(PERSON, null));
        assertThrows(IllegalArgumentException.class,
                () -> new AuthorizationSnapshotCache(null, Duration.ofMinutes(5), clock));
        assertThrows(IllegalArgumentException.class,
                () -> new AuthorizationSnapshotCache(APP, Duration.ofSeconds(-1), clock));
        assertThrows(IllegalArgumentException.class,
                () -> view(PERSON, -1, "agent:read"));
    }

    @Test
    void 缓存键含应用编码_不同应用不串() {
        assertFalse(AuthorizationSnapshotCache.cacheKey("tioka", PERSON)
                .equals(AuthorizationSnapshotCache.cacheKey("tioka-ai", PERSON)));
    }

    /**
     * ADP-08 单一真相源：策略不是本类自己决定的，而是注入进来的。
     *
     * <p>授权能力恒由 {@code policyFor("authz")} 求值为 FAIL_CLOSED，所以默认路径
     * 与 P1 落地时逐字一致。这组用例证明：若待决策 D4 拍板允许授权降级，
     * 只需把 {@code authz} 纳入降级白名单，<b>本类无需任何改动</b>即可生效，
     * 不存在第二处要同步的实现。
     */
    @Test
    void 默认策略为FAIL_CLOSED时过期即拒绝() {
        AuthorizationSnapshotCache cache = new AuthorizationSnapshotCache(APP,
                Duration.ofMinutes(5), clock, DegradePolicy.FAIL_CLOSED, Duration.ofMinutes(10));
        cache.get(PERSON, () -> view(PERSON, 7, "agent:read"));
        clock.advance(Duration.ofMinutes(5));

        assertNull(cache.get(PERSON, () -> null));
        assertEquals(0, cache.size());
        assertFalse(DegradeContext.isMarked(), "拒绝路径不得打降级标记");
    }

    @Test
    void 平台策略改为STALE_CACHE时在陈旧上限内放行并打降级标记() {
        AuthorizationSnapshotCache cache = new AuthorizationSnapshotCache(APP,
                Duration.ofMinutes(5), clock, DegradePolicy.STALE_CACHE, Duration.ofMinutes(10));
        cache.get(PERSON, () -> view(PERSON, 7, "agent:read"));
        clock.advance(Duration.ofMinutes(6));

        AuthorizationSnapshot stale = cache.get(PERSON, () -> null);
        assertEquals(Set.of("agent:read"), stale.permissions(), "上限内返回过期快照");
        assertEquals(7, stale.version());
        assertTrue(DegradeContext.isMarked(), "降级放行必须打标记供响应头与审计使用");
        assertFalse(cache.isLive(PERSON), "条目仍是过期的，下次取用会再次尝试回源");
        assertEquals(Long.valueOf(7), cache.cachedVersion(PERSON), "条目保留，中台持续故障时仍可兜底");
    }

    @Test
    void STALE_CACHE超出陈旧上限仍拒绝() {
        AuthorizationSnapshotCache cache = new AuthorizationSnapshotCache(APP,
                Duration.ofMinutes(5), clock, DegradePolicy.STALE_CACHE, Duration.ofMinutes(10));
        cache.get(PERSON, () -> view(PERSON, 7, "agent:read"));
        clock.advance(Duration.ofMinutes(20));

        assertNull(cache.get(PERSON, () -> null), "超出陈旧上限不得放行");
        assertEquals(0, cache.size(), "不再可用的陈旧条目必须删除");
        assertFalse(DegradeContext.isMarked());
    }

    @Test
    void STALE_CACHE不因版本回退而放行_数据错误不等于中台不可用() {
        AuthorizationSnapshotCache cache = new AuthorizationSnapshotCache(APP,
                Duration.ofMinutes(5), clock, DegradePolicy.STALE_CACHE, Duration.ofMinutes(10));
        cache.get(PERSON, () -> view(PERSON, 7, "agent:read"));
        clock.advance(Duration.ofMinutes(6));

        // 中台可达但返回了更旧的版本（从库延迟 / 乱序）：这是数据错误，不是「不可用」，
        // 降级兜底不得被它触发——否则权限会静默回退到旧版本
        assertNull(cache.get(PERSON, () -> view(PERSON, 6, "agent:read")));
        assertFalse(DegradeContext.isMarked(), "数据错误不得被记为「降级放行」");
    }

    @Test
    void STALE_CACHE不因身份串号而放行() {
        AuthorizationSnapshotCache cache = new AuthorizationSnapshotCache(APP,
                Duration.ofMinutes(5), clock, DegradePolicy.STALE_CACHE, Duration.ofMinutes(10));

        // 无缓存 + 回源身份串号：没有任何可信快照，必须拒绝而非兜底
        assertNull(cache.get(PERSON, () -> view("someone-else", 8, "agent:write")));
        assertEquals(0, cache.size(), "串号数据不得入缓存");
        assertFalse(DegradeContext.isMarked());
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
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
            return instant;
        }
    }
}
