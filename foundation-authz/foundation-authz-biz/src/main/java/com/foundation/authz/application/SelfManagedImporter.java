package com.foundation.authz.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.authz.api.SelfImportApplyResult;
import com.foundation.authz.api.SelfImportItemCmd;
import com.foundation.authz.api.SelfImportPreviewView;
import com.foundation.authz.api.SelfManagedImportApi;
import com.foundation.authz.domain.AuthMode;
import com.foundation.authz.domain.AuthzIdPrefix;
import com.foundation.authz.domain.GrantSource;
import com.foundation.authz.domain.GrantSubjectType;
import com.foundation.authz.domain.Role;
import com.foundation.authz.domain.RoleGrant;
import com.foundation.authz.domain.RoleScope;
import com.foundation.authz.domain.SelfImportKind;
import com.foundation.authz.domain.SelfImportStatus;
import com.foundation.authz.domain.SelfManagedImport;
import com.foundation.authz.domain.SelfManagedImportItem;
import com.foundation.authz.domain.Subsystem;
import com.foundation.authz.infrastructure.RoleGrantRepository;
import com.foundation.authz.infrastructure.RoleRepository;
import com.foundation.authz.infrastructure.SelfManagedImportRepository;
import com.foundation.authz.infrastructure.SubsystemRepository;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 自管子系统 → 集中的一次性导入（F-05 BAS-27 §自管导入）。
 *
 * <p>两段式：{@link #preview} 生成批次并算出差异（不落角色/授权）；{@link #apply} 再生效，
 * 生效后把子系统授权模式改为 {@code CONCENTRATED}；重复 apply 幂等。
 *
 * <p>明细载荷约定：{@code ROLE} 为 {@code {"name":"..."}}（外部键为角色码）；
 * {@code ASSIGNMENT} 为 {@code {"roleCode":"...","userId":"..."}}。
 */
public class SelfManagedImporter implements SelfManagedImportApi {

    private final SubsystemRepository subsystems;
    private final SelfManagedImportRepository imports;
    private final RoleRepository roles;
    private final RoleGrantRepository grants;
    private final AppIdResolver appIds;
    private final ObjectMapper json;

    public SelfManagedImporter(SubsystemRepository subsystems, SelfManagedImportRepository imports,
                               RoleRepository roles, RoleGrantRepository grants, AppIdResolver appIds,
                               ObjectMapper json) {
        this.subsystems = subsystems;
        this.imports = imports;
        this.roles = roles;
        this.grants = grants;
        this.appIds = appIds;
        this.json = json;
    }

    @Override
    @Transactional
    public SelfImportPreviewView preview(String appCode, List<SelfImportItemCmd> items, String operator) {
        Subsystem subsystem = loadSubsystem(appCode);
        String batchId = Ids.newId(AuthzIdPrefix.SELF_IMPORT);
        imports.insert(new SelfManagedImport(batchId, subsystem.id(), SelfImportStatus.PREVIEW, null));

        int newRoles = 0;
        int newAssignments = 0;
        int conflicts = 0;
        List<String> details = new ArrayList<>();
        for (SelfImportItemCmd item : items == null ? List.<SelfImportItemCmd>of() : items) {
            SelfImportKind kind = SelfImportKind.of(item.kind());
            imports.insertItem(new SelfManagedImportItem(Ids.newId(AuthzIdPrefix.SELF_IMPORT_ITEM), batchId, kind,
                    item.externalKey(), item.payload()));
            if (kind == SelfImportKind.ROLE) {
                if (roles.find(item.externalKey()).isPresent()) {
                    conflicts++;
                    details.add("角色已存在：" + item.externalKey());
                } else {
                    newRoles++;
                }
                continue;
            }
            Assignment assignment = assignment(item.payload());
            if (!assignment.valid()) {
                conflicts++;
                details.add("授权明细缺少 roleCode/userId：" + item.externalKey());
            } else if (alreadyGranted(assignment)) {
                conflicts++;
                details.add("授权已存在：" + assignment.roleCode() + " → " + assignment.userId());
            } else {
                newAssignments++;
            }
        }

        String summary = "预览：新增角色 " + newRoles + "，新增授权 " + newAssignments + "，冲突 " + conflicts;
        imports.update(new SelfManagedImport(batchId, subsystem.id(), SelfImportStatus.PREVIEW, summary));
        return new SelfImportPreviewView(batchId, subsystem.id(), SelfImportStatus.PREVIEW.name(),
                newRoles, newAssignments, conflicts, details);
    }

    @Override
    @Transactional
    public SelfImportApplyResult apply(String batchId, String operator) {
        SelfManagedImport batch = imports.findById(batchId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "导入批次不存在：" + batchId));
        Subsystem subsystem = subsystems.findById(batch.subsystemId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "子系统不存在：" + batch.subsystemId()));
        if (batch.status() == SelfImportStatus.APPLIED) {
            return new SelfImportApplyResult(batchId, subsystem.id(), subsystem.authMode().name(), 0, 0, true);
        }

        int createdRoles = 0;
        int createdAssignments = 0;
        for (SelfManagedImportItem item : imports.items(batchId)) {
            if (item.kind() == SelfImportKind.ROLE) {
                if (roles.find(item.externalKey()).isEmpty()) {
                    roles.insert(new Role(item.externalKey(), roleName(item), RoleScope.APP, subsystem.appId(),
                            false, null, Set.of()));
                    createdRoles++;
                }
                continue;
            }
            Assignment assignment = assignment(item.payload());
            if (assignment.valid() && !alreadyGranted(assignment)) {
                grants.insert(new RoleGrant(Ids.newId(AuthzIdPrefix.GRANT), assignment.roleCode(),
                        GrantSubjectType.PERSON, assignment.userId(), false, null, null, GrantSource.SYNC));
                createdAssignments++;
            }
        }

        imports.update(batch.withApplied("应用完成：角色 " + createdRoles + "，授权 " + createdAssignments));
        subsystems.updateAuthMode(subsystem.id(), AuthMode.CONCENTRATED);
        return new SelfImportApplyResult(batchId, subsystem.id(), AuthMode.CONCENTRATED.name(),
                createdRoles, createdAssignments, false);
    }

    private Subsystem loadSubsystem(String appCode) {
        String appId = appIds.resolve(appCode).orElseThrow(
                () -> new BizException(ErrorCode.NOT_FOUND, "应用不存在：" + appCode));
        return subsystems.findByAppId(appId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "子系统不存在：" + appCode));
    }

    private boolean alreadyGranted(Assignment assignment) {
        return grants.list(GrantSubjectType.PERSON, assignment.userId()).stream()
                .anyMatch(grant -> grant.roleCode().equals(assignment.roleCode()));
    }

    private String roleName(SelfManagedImportItem item) {
        JsonNode node = parse(item.payload());
        String name = text(node, "name");
        return name == null || name.isBlank() ? item.externalKey() : name;
    }

    private Assignment assignment(String payload) {
        JsonNode node = parse(payload);
        return new Assignment(text(node, "roleCode"), text(node, "userId"));
    }

    private JsonNode parse(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return json.readTree(payload);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /** 授权明细载荷。 */
    private record Assignment(String roleCode, String userId) {

        boolean valid() {
            return roleCode != null && !roleCode.isBlank() && userId != null && !userId.isBlank();
        }
    }
}
