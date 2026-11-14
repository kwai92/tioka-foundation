package com.foundation.authz.application;

import com.foundation.authz.api.FeatureToggleApi;
import com.foundation.authz.api.FeatureToggleView;
import com.foundation.authz.config.SubsystemProperties;
import com.foundation.authz.domain.AuthzIdPrefix;
import com.foundation.authz.domain.FeatureToggle;
import com.foundation.authz.domain.FeatureToggleRules;
import com.foundation.authz.domain.SubsystemFunction;
import com.foundation.authz.domain.SubsystemModule;
import com.foundation.authz.domain.ToggleScopeType;
import com.foundation.authz.infrastructure.FeatureToggleRepository;
import com.foundation.authz.infrastructure.FunctionRepository;
import com.foundation.authz.infrastructure.ModuleRepository;
import com.foundation.common.id.Ids;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 功能开关（F-05 BAS-27 §功能开关）。
 *
 * <p>停用效果在 BASIS 侧统一生效（快照/菜单/接口三处一致）；
 * {@link #disabled(String, String)} 沿「自身 → 上级」作用域链取第一个显式配置，最具体作用域优先。
 */
public class FeatureToggleService implements FeatureToggleApi {

    private final FeatureToggleRepository toggles;
    private final ModuleRepository modules;
    private final FunctionRepository functions;
    private final SubsystemProperties properties;

    public FeatureToggleService(FeatureToggleRepository toggles, ModuleRepository modules,
                                FunctionRepository functions, SubsystemProperties properties) {
        this.toggles = toggles;
        this.modules = modules;
        this.functions = functions;
        this.properties = properties;
    }

    @Override
    public List<FeatureToggleView> list() {
        return toggles.list().stream().map(FeatureToggleService::toView).toList();
    }

    @Override
    public FeatureToggleView set(String scopeType, String scopeId, boolean enabled, String reason, String operator) {
        ToggleScopeType type = ToggleScopeType.of(scopeType);
        Optional<FeatureToggle> existing = toggles.find(type, scopeId);
        if (existing.isPresent()) {
            FeatureToggle updated = existing.get().withEnabled(enabled, reason);
            toggles.update(updated);
            return toView(updated);
        }
        FeatureToggle created = new FeatureToggle(Ids.newId(AuthzIdPrefix.TOGGLE), type, scopeId, enabled, reason);
        toggles.insert(created);
        return toView(created);
    }

    @Override
    public boolean disabled(String scopeType, String scopeId) {
        ToggleScopeType type = ToggleScopeType.of(scopeType);
        List<FeatureToggleRules.ScopeKey> chain = chain(type, scopeId);
        Map<FeatureToggleRules.ScopeKey, Boolean> values = new LinkedHashMap<>();
        for (FeatureToggleRules.ScopeKey key : chain) {
            toggles.find(key.type(), key.scopeId()).ifPresent(toggle -> values.put(key, toggle.enabled()));
        }
        return FeatureToggleRules.disabled(chain, values);
    }

    /** 作用域链：「自身 → 上级 …」（关闭继承时只含自身）。 */
    private List<FeatureToggleRules.ScopeKey> chain(ToggleScopeType type, String scopeId) {
        List<FeatureToggleRules.ScopeKey> keys = new ArrayList<>();
        keys.add(new FeatureToggleRules.ScopeKey(type, scopeId));
        if (!properties.isToggleInherit()) {
            return keys;
        }
        if (type == ToggleScopeType.FUNCTION) {
            Optional<SubsystemFunction> function = functions.findById(scopeId);
            function.ifPresent(value -> keys.add(new FeatureToggleRules.ScopeKey(ToggleScopeType.MODULE,
                    value.moduleId())));
            function.flatMap(value -> modules.findById(value.moduleId()))
                    .ifPresent(module -> keys.add(new FeatureToggleRules.ScopeKey(ToggleScopeType.SUBSYSTEM,
                            module.subsystemId())));
        } else if (type == ToggleScopeType.MODULE) {
            modules.findById(scopeId).ifPresent(module -> keys.add(new FeatureToggleRules.ScopeKey(
                    ToggleScopeType.SUBSYSTEM, module.subsystemId())));
        }
        return keys;
    }

    private static FeatureToggleView toView(FeatureToggle toggle) {
        return new FeatureToggleView(toggle.scopeType().name(), toggle.scopeId(), toggle.enabled(), toggle.reason());
    }
}
