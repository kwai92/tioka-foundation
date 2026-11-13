package com.foundation.starter.security.snapshot;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
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

/** 快照降级（F-07 §4、§15）：未过期缓存可用；**已过期且回源失败 → 拒绝（不放行）**。 */
class SnapshotDegradeTest {

    private static final String APP = "tioka";
    private static final String PERSON = "per_1";

    private final MutableClock clock = new MutableClock();
    private final StubFetcher fetcher = new StubFetcher();
    private final SnapshotCache cache = new SnapshotCache(
            fetcher, APP, Duration.ofMinutes(5), false, Duration.ofSeconds(10), clock);

    @Test
    void validCacheServedWhenFetcherDown() {
        fetcher.next = snapshot(1);
        cache.load(PERSON);

        fetcher.failure = new IllegalStateException("foundation down");
        AuthzSnapshot served = cache.load(PERSON);

        assertThat(served.version()).isEqualTo(1);
        assertThat(fetcher.loadCount).isEqualTo(1);
    }

    @Test
    void expiredCacheAndFetchFailureDenies() {
        fetcher.next = snapshot(1);
        cache.load(PERSON);
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        fetcher.failure = new IllegalStateException("foundation down");

        Throwable thrown = catchThrowable(() -> cache.load(PERSON));

        assertThat(thrown).isInstanceOf(BizException.class);
        assertThat(((BizException) thrown).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void expiredCacheAndMissingSnapshotDenies() {
        fetcher.next = snapshot(1);
        cache.load(PERSON);
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        fetcher.next = null;

        Throwable thrown = catchThrowable(() -> cache.load(PERSON));

        assertThat(thrown).isInstanceOf(BizException.class);
        assertThat(((BizException) thrown).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void versionDeniesWhenFetchFails() {
        fetcher.failure = new IllegalStateException("foundation down");

        Throwable thrown = catchThrowable(() -> cache.version(PERSON));

        assertThat(thrown).isInstanceOf(BizException.class);
        assertThat(((BizException) thrown).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void versionDeniesWhenSnapshotMissing() {
        fetcher.next = null;

        Throwable thrown = catchThrowable(() -> cache.version(PERSON));

        assertThat(thrown).isInstanceOf(BizException.class);
        assertThat(((BizException) thrown).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void versionServedFromLiveCache() {
        fetcher.next = snapshot(4);
        cache.load(PERSON);

        assertThat(cache.version(PERSON)).isEqualTo(4);
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
