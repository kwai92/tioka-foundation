package com.foundation.sync.domain;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** F-08 BAS-11 §5：本地字段锁定（交集、过滤、CSV 解析与序列化）。 */
class LocalLockResolverTest {

    private final LocalLockResolver resolver = new LocalLockResolver();

    @Test
    void lockedAmongReturnsIntersection() {
        Set<String> locked = resolver.lockedAmong(Set.of("name", "mobile"), Set.of("mobile", "email"));

        assertThat(locked).containsExactly("mobile");
    }

    @Test
    void lockedAmongReturnsEmptyOnEmptyInputs() {
        assertThat(resolver.lockedAmong(Set.of(), Set.of("name"))).isEmpty();
        assertThat(resolver.lockedAmong(Set.of("name"), Set.of())).isEmpty();
        assertThat(resolver.lockedAmong(null, Set.of("name"))).isEmpty();
        assertThat(resolver.lockedAmong(Set.of("name"), null)).isEmpty();
    }

    @Test
    void filterFieldsRemovesLockedOnes() {
        Map<String, Object> filtered = resolver.filterFields(
                Map.of("name", "张三", "mobile", "138"), Set.of("name"));

        assertThat(filtered).containsOnlyKeys("mobile");
        assertThat(filtered).containsEntry("mobile", "138");
    }

    @Test
    void filterFieldsReturnsEmptyMapOnNullFields() {
        assertThat(resolver.filterFields(null, Set.of("name"))).isEmpty();
    }

    @Test
    void filterFieldsKeepsAllWhenNoLock() {
        Map<String, Object> filtered = resolver.filterFields(Map.of("name", "张三"), Set.of());

        assertThat(filtered).containsOnlyKeys("name");
    }

    @Test
    void parseSplitsTrimsAndSkipsBlanks() {
        Set<String> parsed = resolver.parse(" name , ,mobile,");

        assertThat(parsed).containsExactlyInAnyOrder("name", "mobile");
    }

    @Test
    void parseBlankReturnsEmptySet() {
        assertThat(resolver.parse(null)).isEmpty();
        assertThat(resolver.parse("")).isEmpty();
        assertThat(resolver.parse("   ")).isEmpty();
        assertThat(resolver.parse(" , , ")).isEmpty();
    }

    @Test
    void serializeJoinsWithCommaKeepingOrder() {
        Set<String> fields = new LinkedHashSet<>();
        fields.add("name");
        fields.add("mobile");

        assertThat(resolver.serialize(fields)).isEqualTo("name,mobile");
    }

    @Test
    void serializeEmptyReturnsBlank() {
        assertThat(resolver.serialize(Set.of())).isEmpty();
        assertThat(resolver.serialize(null)).isEmpty();
    }

    @Test
    void parseAndSerializeRoundTrip() {
        Set<String> fields = resolver.parse("name,mobile, email");

        assertThat(resolver.parse(resolver.serialize(fields))).isEqualTo(fields);
    }
}
