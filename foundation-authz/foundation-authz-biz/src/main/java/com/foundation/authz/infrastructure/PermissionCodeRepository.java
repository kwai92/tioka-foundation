package com.foundation.authz.infrastructure;

import com.foundation.authz.domain.PermissionCode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 权限码仓储（F-05 BAS-05 §领域模型）。 */
public interface PermissionCodeRepository {

    /** 应用全部权限码（含 {@code OFFLINE}，供清单差异比对）。 */
    List<PermissionCode> listByApp(String appId);

    /** 全部在线权限码（{@code GET /api/permissions} 未指定应用时的目录）。 */
    List<PermissionCode> listActive();

    /** 按主键查询。 */
    Optional<PermissionCode> findById(String id);

    /** 新增权限码（清单声明）。 */
    void insert(PermissionCode permissionCode);

    /** 用清单覆盖说明与风险级别并复位为在线。 */
    void updateFromManifest(PermissionCode permissionCode);

    /** 标下线并记下线时间。 */
    void offline(String id, Instant offlineAt);
}
