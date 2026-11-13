package com.foundation.starter.security.snapshot;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 授权快照本地缓存（F-07 §4、§15）：命中不回源、事件删缓存、TTL 到期回源、版本不串缓存。 */
class SnapshotCacheTest {

    private static final String APP = "tioka";
    private static final String PERSON = "per_1";

    private final MutableClock clock = new MutableClock();
    private final StubFetcher fetcher = new StubFetcher();
    private final SnapshotCache cache = new SnapshotCache(
            fetcher, APP, Duration.ofMinutes(5), false, Duration.ofSeconds(10), clock);

    @Test
    void cachesHitWithoutRefetch() {
        fetcher.next = snapshot(1);

        AuthzSnapshot first = cache.load(PERSON);
        AuthzSnapshot second = cache.load(PERSON);

        assertThat(first).isSameAs(second);
        assertThat(fetcher.loadCount).isEqualTo(1);
        assertThat(cache.isCached(PERSON)).isTrue();
    }

    @Test
    void evictsOnAuthzChanged() {
        fetcher.next = snapshot(1);
        cache.load(PERSON);

        cache.evict(PERSON);

        assertThat(cache.isCached(PERSON)).isFalse();
        assertThat(cache.cachedCount()).isZero();
        cache.load(PERSON);
        assertThat(fetcher.loadCount).isEqualTo(2);
    }

    @Test
    void ttlExpiryForcesRefetch() {
        fetcher.next = snapshot(1);
        cache.load(PERSON);

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));

        assertThat(cache.isCached(PERSON)).isFalse();
        cache.load(PERSON);
        assertThat(fetcher.loadCount).isEqualTo(2);
    }

    @Test
    void versionChangeDoesNotReuseCache() {
        fetcher.next = snapshot(1);
        cache.load(PERSON);

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        fetcher.next = snapshot(2);
        AuthzSnapshot latest = cache.load(PERSON);

        assertThat(latest.version()).isEqualTo(2);
        // 旧版本条目被丢弃，不串缓存
        assertThat(cache.cachedCount()).isEqualTo(1);
    }

    @Test
    void cacheKeyCarriesAppPersonAndVersion() {
        assertThat(SnapshotCache.cacheKey(APP, PERSON, 7)).isEqualTo("fdn:snap:tioka:per_1:7");
    }

    private static AuthzSnapshot snapshot(long version) {
        return new AuthzSnapshot(PERSON, APP, version, Set.of(), Set.of(),
                List.of(), DeptScope.ofAll(), Map.of(), null);
    }

    /** 可变时钟：便于推进 TTL。 */
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

    /** 计数回源桩。 */
    private static final class StubFetcher implements AuthzSnapshotFetcher {

        private AuthzSnapshot next;
        private RuntimeException failure;
        private int loadCount;

        @Override
        public AuthzSnapshot load(String appCode, String personId) {
            loadCount++;
            if (failure != null) {
                throw failure;
            }
            return next;
        }

        @Override
        public Long version(String appCode, String personId) {
            if (failure != null) {
                throw failure;
            }
            return next == null ? null : next.version();
        }
    }
}
