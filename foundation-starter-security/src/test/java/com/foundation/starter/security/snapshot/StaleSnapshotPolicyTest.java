package com.foundation.starter.security.snapshot;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.starter.core.context.DegradeContext;
import com.foundation.starter.security.config.FoundationAuthzProperties;
import org.junit.jupiter.api.AfterEach;
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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADP-08：授权快照的降级策略接线。
 *
 * <p>默认仍是 deny（由 {@link SnapshotDegradeTest} 覆盖）；这里验证只有显式
 * {@code fail-mode=stale} 才会在陈旧上限内放行，并且必须留降级标记。
 */
class StaleSnapshotPolicyTest {

    private static final String APP = "tioka";
    private static final String PERSON = "per_1";

    @AfterEach
    void cleanUp() {
        DegradeContext.clear();
    }

    @Test
    void stale模式在陈旧上限内放行并打降级标记() {
        MutableClock clock = new MutableClock();
        StubFetcher fetcher = new StubFetcher();
        SnapshotCache cache = cache(clock, fetcher, Duration.ofMinutes(2));
        fetcher.next = snapshot(1);
        cache.load(PERSON);

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        fetcher.failure = new IllegalStateException("foundation down");

        assertThat(cache.load(PERSON).version()).isEqualTo(1);
        assertTrue(DegradeContext.isMarked(), "降级放行必须打标记，供 Web 层透传响应头");
    }

    @Test
    void version接口同样遵循stale策略() {
        MutableClock clock = new MutableClock();
        StubFetcher fetcher = new StubFetcher();
        SnapshotCache cache = cache(clock, fetcher, Duration.ofMinutes(2));
        fetcher.next = snapshot(4);
        cache.load(PERSON);
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        fetcher.failure = new IllegalStateException("foundation down");

        assertThat(cache.version(PERSON)).isEqualTo(4);
        assertTrue(DegradeContext.isMarked());
    }

    @Test
    void 超出陈旧上限仍拒绝且不打标记() {
        MutableClock clock = new MutableClock();
        StubFetcher fetcher = new StubFetcher();
        SnapshotCache cache = cache(clock, fetcher, Duration.ofSeconds(0));
        fetcher.next = snapshot(1);
        cache.load(PERSON);
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        fetcher.failure = new IllegalStateException("foundation down");

        Throwable thrown = catchThrowable(() -> cache.load(PERSON));

        assertThat(thrown).isInstanceOf(BizException.class);
        assertThat(((BizException) thrown).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        assertFalse(DegradeContext.isMarked());
    }

    @Test
    void 无过期缓存时stale模式也拒绝() {
        MutableClock clock = new MutableClock();
        StubFetcher fetcher = new StubFetcher();
        fetcher.failure = new IllegalStateException("foundation down");

        Throwable thrown = catchThrowable(() -> cache(clock, fetcher, Duration.ofMinutes(2)).load(PERSON));

        assertThat(thrown).isInstanceOf(BizException.class);
    }

    @Test
    void 默认配置与非法failMode() {
        FoundationAuthzProperties.Snapshot snapshot = new FoundationAuthzProperties().getSnapshot();
        assertFalse(snapshot.isStaleAllowed(), "缺省必须 deny");
        assertThat(snapshot.getStaleMaxAge()).isEqualTo(Duration.ofMinutes(10));

        snapshot.setFailMode("stale");
        assertTrue(snapshot.isStaleAllowed());
        snapshot.setFailMode("fail-open");
        assertThrows(IllegalArgumentException.class, snapshot::isStaleAllowed);
    }

    private static SnapshotCache cache(MutableClock clock, StubFetcher fetcher, Duration maxStale) {
        return new SnapshotCache(fetcher, APP, Duration.ofMinutes(5), false,
                Duration.ofSeconds(10), clock, true, maxStale);
    }

    private static AuthzSnapshot snapshot(long version) {
        return new AuthzSnapshot(PERSON, APP, version, Set.of(), Set.of(),
                List.of(), DeptScope.ofAll(), Map.of(), null);
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

    private static final class StubFetcher implements AuthzSnapshotFetcher {

        private AuthzSnapshot next;
        private RuntimeException failure;

        @Override
        public AuthzSnapshot load(String appCode, String personId) {
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
