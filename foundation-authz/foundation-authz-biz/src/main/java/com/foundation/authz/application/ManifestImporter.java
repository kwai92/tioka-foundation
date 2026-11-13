package com.foundation.authz.application;

import com.foundation.app.api.AppManifestVersionApi;
import com.foundation.authz.api.AppManifestImportedEvent;
import com.foundation.authz.api.ManifestApi;
import com.foundation.authz.api.ManifestImportResult;
import com.foundation.authz.domain.AppManifest;
import com.foundation.authz.domain.AuthzIdPrefix;
import com.foundation.authz.domain.LifecycleStatus;
import com.foundation.authz.domain.ManifestDiff;
import com.foundation.authz.domain.ManifestDocument;
import com.foundation.authz.domain.ManifestHasher;
import com.foundation.authz.domain.ManifestMenu;
import com.foundation.authz.domain.ManifestParser;
import com.foundation.authz.domain.ManifestPermission;
import com.foundation.authz.domain.Menu;
import com.foundation.authz.domain.PermissionCode;
import com.foundation.authz.infrastructure.AppManifestRepository;
import com.foundation.authz.infrastructure.MenuRepository;
import com.foundation.authz.infrastructure.PermissionCodeRepository;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.api.event.EventProperties;
import com.foundation.common.api.event.OutboxAppender;
import com.foundation.common.id.Ids;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 权限清单导入（F-05 BAS-05 §清单导入规则）。
 *
 * <p>流程：规范化 + SHA-256 得内容哈希 → 命中 {@code (app_id, content_hash)} 幂等返回 →
 * 版本必须前进 → 计算差异并落库 → 追加导入记录、推进清单版本 → outbox 发
 * {@code fdn.app.manifest_imported}。落库与事件同事务（S15 §14.2）。
 */
public class ManifestImporter implements ManifestApi {

    private final AppManifestVersionApi versions;
    private final AppIdResolver appIds;
    private final AppManifestRepository manifests;
    private final MenuRepository menus;
    private final PermissionCodeRepository permissions;
    private final OutboxAppender outbox;
    private final EventProperties events;

    private final ManifestParser parser = new ManifestParser();
    private final ManifestDiffCalculator diffCalculator = new ManifestDiffCalculator();

    public ManifestImporter(AppManifestVersionApi versions, AppIdResolver appIds, AppManifestRepository manifests,
                            MenuRepository menus, PermissionCodeRepository permissions, OutboxAppender outbox,
                            EventProperties events) {
        this.versions = versions;
        this.appIds = appIds;
        this.manifests = manifests;
        this.menus = menus;
        this.permissions = permissions;
        this.outbox = outbox;
        this.events = events;
    }

    @Override
    @Transactional
    public ManifestImportResult importManifest(String appCode, byte[] manifestBytes, String importSource,
                                               String operator) {
        String appId = appIds.resolve(appCode)
                .orElseThrow(() -> new BizException(ErrorCode.BAD_REQUEST, "应用编码不能为空"));
        String actor = actorOf(operator, importSource);
        ManifestDocument document = parser.parse(manifestBytes);
        String contentHash = ManifestHasher.contentHash(document);

        Optional<AppManifest> imported = manifests.findByHash(appId, contentHash);
        if (imported.isPresent()) {
            AppManifest record = imported.get();
            return new ManifestImportResult(appCode, record.version(), contentHash, true,
                    document.menus().size(), document.permissions().size(), 0, record.importedAt());
        }

        long currentVersion = versions.currentVersion(appCode);
        if (document.version() <= currentVersion) {
            throw new BizException(ErrorCode.CONFLICT, "清单版本只能前进：当前 " + currentVersion
                    + "，本次 " + document.version());
        }

        List<Menu> existingMenus = menus.listByApp(appId);
        ManifestDiff diff = diffCalculator.diff(document, existingMenus, permissions.listByApp(appId));

        Instant importedAt = Instant.now();
        applyMenus(appId, existingMenus, diff);
        applyPermissions(appId, diff, importedAt);

        manifests.insert(new AppManifest(Ids.newId(AuthzIdPrefix.MANIFEST), appId, document.version(),
                contentHash, importedAt, actor));
        versions.advance(appCode, document.version(), actor);

        outbox.append(events.getTopic(), AppManifestImportedEvent.EVENT_TYPE, appCode,
                payload(appCode, document, contentHash, diff.offlineCount(), actor, importedAt));

        return new ManifestImportResult(appCode, document.version(), contentHash, false,
                document.menus().size(), document.permissions().size(), diff.offlineCount(), importedAt);
    }

    /** 新增项直接生效；变更项只覆盖清单来源字段，管理台改过的显示属性保持不变；消失项标下线。 */
    private void applyMenus(String appId, List<Menu> existingMenus, ManifestDiff diff) {
        Map<String, Menu> existingByCode = new LinkedHashMap<>();
        existingMenus.forEach(menu -> existingByCode.put(menu.code(), menu));

        Map<String, String> idByCode = new LinkedHashMap<>();
        existingByCode.forEach((code, menu) -> idByCode.put(code, menu.id()));
        for (ManifestMenu added : diff.addedMenus()) {
            idByCode.put(added.code(), Ids.newId(AuthzIdPrefix.MENU));
        }

        for (ManifestMenu added : diff.addedMenus()) {
            menus.insert(toMenu(appId, added, idByCode, LifecycleStatus.ACTIVE,
                    added.name(), added.icon(), added.sort(), added.visible()));
        }
        for (ManifestDiff.ChangedMenu changed : diff.changedMenus()) {
            Menu existing = changed.existing();
            menus.updateFromManifest(toMenu(appId, changed.declared(), idByCode, LifecycleStatus.ACTIVE,
                    existing.name(), existing.icon(), existing.sort(), existing.visible()));
        }
        for (Menu offline : diff.offlineMenus()) {
            menus.offline(offline.id());
        }
    }

    private void applyPermissions(String appId, ManifestDiff diff, Instant importedAt) {
        for (ManifestPermission added : diff.addedPermissions()) {
            permissions.insert(new PermissionCode(Ids.newId(AuthzIdPrefix.PERMISSION_CODE), appId, added.code(),
                    added.name(), added.riskLevel(), LifecycleStatus.ACTIVE, importedAt, null));
        }
        for (ManifestDiff.ChangedPermission changed : diff.changedPermissions()) {
            PermissionCode existing = changed.existing();
            permissions.updateFromManifest(new PermissionCode(existing.id(), appId, existing.code(),
                    changed.declared().name(), changed.declared().riskLevel(), LifecycleStatus.ACTIVE,
                    existing.firstSeenAt(), null));
        }
        for (PermissionCode offline : diff.offlinePermissions()) {
            permissions.offline(offline.id(), importedAt);
        }
    }

    private static Menu toMenu(String appId, ManifestMenu declared, Map<String, String> idByCode, String status,
                               String name, String icon, int sort, boolean visible) {
        String id = idByCode.get(declared.code());
        String parentId = declared.parentCode() == null ? null : idByCode.get(declared.parentCode());
        if (declared.parentCode() != null && parentId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "上级菜单编码不存在：" + declared.parentCode());
        }
        return new Menu(id, appId, declared.code(), name, declared.type().name(), parentId,
                declared.routePath(), declared.component(), icon, sort, visible, declared.permissionCode(), status);
    }

    private static Map<String, Object> payload(String appCode, ManifestDocument document, String contentHash,
                                               int offlineCount, String actor, Instant importedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appCode", appCode);
        payload.put("version", document.version());
        payload.put("contentHash", contentHash);
        payload.put("menuCount", document.menus().size());
        payload.put("permissionCodeCount", document.permissions().size());
        payload.put("offlineCount", offlineCount);
        payload.put("importedBy", actor);
        payload.put("importedAt", importedAt.toString());
        return payload;
    }

    private static String actorOf(String operator, String importSource) {
        if (operator != null && !operator.isBlank()) {
            return operator;
        }
        return importSource != null && !importSource.isBlank() ? importSource : "system";
    }
}
