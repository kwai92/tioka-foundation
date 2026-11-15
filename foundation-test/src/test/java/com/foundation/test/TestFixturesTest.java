package com.foundation.test;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TestFixturesTest {

    @Test
    void utcOfBuildsExplicitUtcWallClock() {
        LocalDateTime value = TestFixtures.utcOf(2026, 3, 1, 0, 0, 0);
        assertThat(value.getYear()).isEqualTo(2026);
        assertThat(value.getMonthValue()).isEqualTo(3);
        assertThat(value.getDayOfMonth()).isEqualTo(1);
        assertThat(value.getHour()).isZero();
        assertThat(value.getMinute()).isZero();
        assertThat(value.getSecond()).isZero();
    }

    @Test
    void utcOfEpochZeroIsUnixEpoch() {
        assertThat(TestFixtures.utcOf(0L)).isEqualTo(LocalDateTime.of(1970, 1, 1, 0, 0));
    }

    @Test
    void uniqueSuffixIsUnique() {
        assertThat(TestFixtures.uniqueSuffix()).isNotEqualTo(TestFixtures.uniqueSuffix());
    }
}
