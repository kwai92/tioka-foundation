package com.foundation.starter.core.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ADP-08：运行模式解析与兼容基线（未配置时行为与改造前完全一致）。 */
class FoundationRuntimeModeTest {

    @Test
    void 缺省为接入中台模式() {
        assertEquals(FoundationRuntimeMode.FOUNDATION, FoundationRuntimeMode.parse(null));
        assertEquals(FoundationRuntimeMode.FOUNDATION, FoundationRuntimeMode.parse("  "));
        assertEquals(FoundationRuntimeMode.FOUNDATION,
                FoundationRuntimeMode.from(new MockEnvironment()));
        assertTrue(FoundationRuntimeMode.FOUNDATION.requiresRemoteFoundation());
        assertFalse(FoundationRuntimeMode.FOUNDATION.isStandalone());
    }

    @Test
    void 宽松解析三种模式_支持大小写连字符与下划线() {
        assertEquals(FoundationRuntimeMode.FOUNDATION, FoundationRuntimeMode.parse(" FOUNDATION "));
        assertEquals(FoundationRuntimeMode.LOCAL, FoundationRuntimeMode.parse("Local"));
        assertEquals(FoundationRuntimeMode.SELF_MANAGED, FoundationRuntimeMode.parse("self-managed"));
        assertEquals(FoundationRuntimeMode.SELF_MANAGED, FoundationRuntimeMode.parse("SELF_MANAGED"));
        assertEquals(FoundationRuntimeMode.SELF_MANAGED, FoundationRuntimeMode.parse("selfmanaged"));
    }

    @Test
    void 非法模式直接拒绝而不是静默当成默认() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> FoundationRuntimeMode.parse("hybrid"));
        assertTrue(ex.getMessage().contains("foundation|local|self-managed"));
    }

    @Test
    void 从环境读取模式() {
        MockEnvironment env = new MockEnvironment()
                .withProperty(FoundationRuntimeMode.PROPERTY, "local");
        assertEquals(FoundationRuntimeMode.LOCAL, FoundationRuntimeMode.from(env));
        MockEnvironment illegal = new MockEnvironment()
                .withProperty(FoundationRuntimeMode.PROPERTY, "oops");
        assertThrows(IllegalArgumentException.class, () -> FoundationRuntimeMode.from(illegal));
    }

    @Test
    void 独立模式不装配中台出站通道() {
        for (FoundationRuntimeMode mode : FoundationRuntimeMode.values()) {
            assertEquals(mode == FoundationRuntimeMode.FOUNDATION,
                    mode.requiresRemoteFoundation(), mode.name());
            assertEquals(mode != FoundationRuntimeMode.FOUNDATION,
                    mode.isStandalone(), mode.name());
        }
    }
}
