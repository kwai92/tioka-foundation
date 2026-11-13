package com.foundation.authz.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 权限码与角色码的纯规则校验（F-05 BAS-05 §权限码）。
 *
 * <p>全局角色（{@code scope=SYSTEM}）只允许含 Foundation 自身权限码（{@code fdn:*}）。
 */
public final class PermissionCatalog {

    /** 全局角色专属权限码前缀。 */
    public static final String PLATFORM_PERMISSION_PREFIX = "fdn:";

    /** 内置全局角色码（V4 迁移种子；内置角色不可删）。 */
    public static final Set<String> BUILT_IN_ROLE_CODES =
            Set.of("system_admin", "security_admin", "audit_admin", "org_admin", "app_admin");

    /** 权限码：{@code 资源:动作}，至少一个冒号。 */
    private static final Pattern PERMISSION_CODE =
            Pattern.compile("^[a-z][a-z0-9_-]*(:[a-z0-9_-]+)+$");

    /** 菜单编码：字母数字与 {@code . _ - :}（清单内唯一）。 */
    private static final Pattern MENU_CODE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.:-]*$");

    private PermissionCatalog() {
    }

    /** 校验权限码格式，非法抛 {@code BAD_REQUEST}。 */
    public static void validatePermissionCode(String code) {
        if (code == null || !PERMISSION_CODE.matcher(code).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法权限码（应形如 资源:动作）：" + code);
        }
    }

    /** 校验菜单编码格式，非法抛 {@code BAD_REQUEST}。 */
    public static void validateMenuCode(String code) {
        if (code == null || !MENU_CODE.matcher(code).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法菜单编码：" + code);
        }
    }

    /** 全局角色是否只含平台权限码。 */
    public static boolean platformScopedOnly(Set<String> permissionCodes) {
        return permissionCodes.stream().allMatch(code -> code.startsWith(PLATFORM_PERMISSION_PREFIX));
    }

    /** 权限码是否属于平台权限码（全局角色可用）。 */
    public static boolean platformCode(String code) {
        return code != null && code.startsWith(PLATFORM_PERMISSION_PREFIX);
    }
}
