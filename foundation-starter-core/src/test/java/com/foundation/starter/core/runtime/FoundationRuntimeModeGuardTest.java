package com.foundation.starter.core.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ADP-08：启动期契约提示——只告警不误伤（接入方可自行注册本地实现）。 */
class FoundationRuntimeModeGuardTest {

    private static List<String> warnings(FoundationRuntimeMode mode, MockEnvironment env) {
        return new FoundationRuntimeModeGuard(mode, env).warnings();
    }

    @Test
    void 接入中台模式无告警() {
        assertTrue(warnings(FoundationRuntimeMode.FOUNDATION, new MockEnvironment()).isEmpty());
    }

    @Test
    void 本地模式未开启种子时提示补齐本地实现() {
        List<String> warnings = warnings(FoundationRuntimeMode.LOCAL, new MockEnvironment());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("DictClient"));
        assertTrue(warnings.get(0).contains(FoundationRuntimeModeGuard.LOCAL_SEED_PROPERTY));
    }

    @Test
    void 本地模式开启种子后不再提示补齐实现() {
        MockEnvironment env = new MockEnvironment()
                .withProperty(FoundationRuntimeModeGuard.LOCAL_SEED_PROPERTY, "true");
        assertTrue(warnings(FoundationRuntimeMode.LOCAL, env).isEmpty());
    }

    @Test
    void 自管模式额外声明授权导入属F2() {
        MockEnvironment env = new MockEnvironment()
                .withProperty(FoundationRuntimeModeGuard.LOCAL_SEED_PROPERTY, "true");
        List<String> warnings = warnings(FoundationRuntimeMode.SELF_MANAGED, env);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("F2"));
        assertFalse(warnings.get(0).contains("DictClient"));
    }

    @Test
    void 总开关关闭时提示模式不会改变装配() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("foundation.enabled", "false");
        List<String> warnings = warnings(FoundationRuntimeMode.LOCAL, env);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("foundation.enabled=false"));
    }

    @Test
    void 空环境与空模式按默认值安全处理() {
        assertTrue(new FoundationRuntimeModeGuard(null, null).warnings().isEmpty());
        assertTrue(warnings(FoundationRuntimeMode.FOUNDATION, null).isEmpty());
    }

    @Test
    void validate返回与warnings一致的告警() {
        MockEnvironment env = new MockEnvironment();
        FoundationRuntimeModeGuard guard = new FoundationRuntimeModeGuard(FoundationRuntimeMode.LOCAL, env);
        assertEquals(guard.warnings(), guard.validate());
    }
}
