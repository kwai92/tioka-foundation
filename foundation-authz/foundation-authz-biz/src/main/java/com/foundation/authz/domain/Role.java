package com.foundation.authz.domain;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 角色（F-05 BAS-05 §领域模型）。
 *
 * @param code        角色编码（主键，创建后不可改）
 * @param name        名称
 * @param scope       见 {@link RoleScope}
 * @param appId       {@code scope=APP} 时为所属应用，{@code scope=SYSTEM} 时为 {@code null}
 * @param builtIn     内置标志（内置角色不可删）
 * @param dataScope   数据范围（BAS-06 细化）
 * @param permissions 权限码集合
 */
public record Role(String code,
                   String name,
                   RoleScope scope,
                   String appId,
                   boolean builtIn,
                   String dataScope,
                   Set<String> permissions) {

    /** 数据范围缺省值（BAS-06 细化前的最小可用值）。 */
    public static final String DEFAULT_DATA_SCOPE = "SELF";

    public Role {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(scope, "scope");
        dataScope = dataScope == null || dataScope.isBlank() ? DEFAULT_DATA_SCOPE : dataScope;
        permissions = permissions == null ? Set.of() : new LinkedHashSet<>(permissions);
    }

    public boolean systemScoped() {
        return RoleScope.SYSTEM == scope;
    }
}
