package com.foundation.sync.spi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-08 BAS-11 §7：幂等键 {@code SyncKey(来源, 外部 ID)} 的构造与归属判定。 */
class SyncKeyTest {

    @Test
    void constructorRejectsNullParts() {
        assertThatThrownBy(() -> new SyncKey(null, "u1")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SyncKey("FEISHU", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void ofTrimsBothParts() {
        SyncKey key = SyncKey.of("  FEISHU  ", "  u1  ");

        assertThat(key.sourceType()).isEqualTo("FEISHU");
        assertThat(key.externalId()).isEqualTo("u1");
    }

    @Test
    void ofRejectsBlankParts() {
        assertThatThrownBy(() -> SyncKey.of("", "u1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SyncKey.of("FEISHU", "   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SyncKey.of(null, "u1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalityBySourceAndExternalId() {
        assertThat(SyncKey.of("FEISHU", "u1")).isEqualTo(SyncKey.of("FEISHU", "u1"));
        assertThat(SyncKey.of("FEISHU", "u1")).isNotEqualTo(SyncKey.of("LDAP", "u1"));
        assertThat(SyncKey.of("FEISHU", "u1").hashCode()).isEqualTo(SyncKey.of("FEISHU", "u1").hashCode());
    }

    @Test
    void belongsToIsCaseInsensitiveAndTrims() {
        SyncKey key = SyncKey.of("FEISHU", "u1");

        assertThat(key.belongsTo("feishu")).isTrue();
        assertThat(key.belongsTo("  FeiShu ")).isTrue();
        assertThat(key.belongsTo("LDAP")).isFalse();
        assertThat(key.belongsTo(null)).isFalse();
    }
}
