package com.foundation.authz.domain;

import com.foundation.authz.domain.FeatureToggleRules.ScopeKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 功能开关继承（F-05 BAS-27 §功能开关）。 */
class FeatureToggleInheritanceTest {

    private static final ScopeKey SUBSYSTEM = new ScopeKey(ToggleScopeType.SUBSYSTEM, "sub_1");
    private static final ScopeKey MODULE = new ScopeKey(ToggleScopeType.MODULE, "mod_1");
    private static final ScopeKey FUNCTION = new ScopeKey(ToggleScopeType.FUNCTION, "fun_1");

    /** 自身 → 上级 的作用域链。 */
    private static List<ScopeKey> chain(ScopeKey... selfFirst) {
        return List.of(selfFirst);
    }

    @Test
    void subsystemDisabledDisablesModule() {
        assertThat(FeatureToggleRules.disabled(chain(MODULE, SUBSYSTEM), Map.of(SUBSYSTEM, false)))
                .isTrue();
    }

    @Test
    void moduleDisabledDisablesFunction() {
        assertThat(FeatureToggleRules.disabled(chain(FUNCTION, MODULE, SUBSYSTEM), Map.of(MODULE, false)))
                .isTrue();
    }

    @Test
    void functionExplicitEnableOverridesModuleDisabled() {
        assertThat(FeatureToggleRules.disabled(chain(FUNCTION, MODULE, SUBSYSTEM),
                Map.of(FUNCTION, true, MODULE, false))).isFalse();
    }

    @Test
    void mostSpecificConfigWins() {
        assertThat(FeatureToggleRules.disabled(chain(FUNCTION, MODULE, SUBSYSTEM),
                Map.of(FUNCTION, false, MODULE, true, SUBSYSTEM, true))).isTrue();
    }

    @Test
    void noConfigMeansNotDisabled() {
        assertThat(FeatureToggleRules.disabled(chain(FUNCTION, MODULE, SUBSYSTEM), Map.of())).isFalse();
    }
}
