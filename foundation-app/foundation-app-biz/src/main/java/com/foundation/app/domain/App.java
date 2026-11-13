package com.foundation.app.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/**
 * 应用（F-05 BAS-04 §领域模型）。
 *
 * <p>{@link #updateProfile} 不含编码参数：应用编码登记后不可改。
 */
public record App(String id,
                  String code,
                  String name,
                  String icon,
                  String accessUrl,
                  String kind,
                  String ownerUserId,
                  String status,
                  long manifestVersion) {

    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    /** 登记新应用：状态 {@code ENABLED}、清单版本 0（清单导入时递增，见 BAS-05）。 */
    public static App register(String id, String code, String name, String icon, String accessUrl, String kind,
                               String ownerUserId) {
        return new App(id, code, requireName(name), icon, accessUrl, kind,
                requireOwner(ownerUserId), STATUS_ENABLED, 0L);
    }

    /** 变更名称/图标/地址/负责人/状态；编码与清单版本不变。 */
    public App updateProfile(String newName, String newIcon, String newAccessUrl,
                             String newOwnerUserId, String newStatus) {
        return new App(id, code, requireName(newName), newIcon, newAccessUrl, kind,
                requireOwner(newOwnerUserId), requireStatus(newStatus), manifestVersion);
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "应用名称不能为空");
        }
        return value.trim();
    }

    private static String requireOwner(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "应用负责人不能为空");
        }
        return value.trim();
    }

    private static String requireStatus(String value) {
        if (!STATUS_ENABLED.equals(value) && !STATUS_DISABLED.equals(value)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "应用状态只能是 ENABLED/DISABLED：" + value);
        }
        return value;
    }
}
