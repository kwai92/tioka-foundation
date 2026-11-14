package com.foundation.authz.application;

import com.foundation.app.api.AppApi;
import com.foundation.app.api.AppManifestVersionApi;
import com.foundation.app.api.AppRegistrationCmd;
import com.foundation.app.api.AppUpdateCmd;
import com.foundation.app.api.AppView;
import com.foundation.authz.api.DataScopeApi;
import com.foundation.authz.api.DataScopeView;
import com.foundation.authz.api.FeatureToggleApi;
import com.foundation.authz.api.FeatureToggleView;
import com.foundation.authz.api.PersonDimensionCmd;
import com.foundation.authz.api.RoleDataScopeCmd;
import com.foundation.authz.api.RoleDataScopeView;
import com.foundation.authz.api.ScopeView;
import com.foundation.authz.domain.AppManifest;
import com.foundation.authz.domain.AuthMode;
import com.foundation.authz.domain.AuthzSnapshot;
import com.foundation.authz.domain.DataDimension;
import com.foundation.authz.domain.DimensionValue;
import com.foundation.authz.domain.FeatureToggle;
import com.foundation.authz.domain.GrantSource;
import com.foundation.authz.domain.GrantSubjectType;
import com.foundation.authz.domain.LifecycleStatus;
import com.foundation.authz.domain.Menu;
import com.foundation.authz.domain.OrgAdminScope;
import com.foundation.authz.domain.PermissionCode;
import com.foundation.authz.domain.PersonDimensionGrant;
import com.foundation.authz.domain.Role;
import com.foundation.authz.domain.RoleDataScope;
import com.foundation.authz.domain.RoleDimensionValue;
import com.foundation.authz.domain.RoleGrant;
import com.foundation.authz.domain.RoleMutex;
import com.foundation.authz.domain.SelfManagedImport;
import com.foundation.authz.domain.SelfManagedImportItem;
import com.foundation.authz.domain.SubjectMembership;
import com.foundation.authz.domain.Subsystem;
import com.foundation.authz.domain.SubsystemFunction;
import com.foundation.authz.domain.SubsystemModule;
import com.foundation.authz.domain.ToggleScopeType;
import com.foundation.authz.infrastructure.AppManifestRepository;
import com.foundation.authz.infrastructure.DataDimensionRepository;
import com.foundation.authz.infrastructure.DimensionValueRepository;
import com.foundation.authz.infrastructure.FeatureToggleRepository;
import com.foundation.authz.infrastructure.FunctionRepository;
import com.foundation.authz.infrastructure.MenuRepository;
import com.foundation.authz.infrastructure.ModuleRepository;
import com.foundation.authz.infrastructure.OrgAdminScopeRepository;
import com.foundation.authz.infrastructure.PermissionCodeRepository;
import com.foundation.authz.infrastructure.PersonDimensionRepository;
import com.foundation.authz.infrastructure.RoleDataScopeRepository;
import com.foundation.authz.infrastructure.RoleDimensionValueRepository;
import com.foundation.authz.infrastructure.RoleGrantRepository;
import com.foundation.authz.infrastructure.RoleMutexRepository;
import com.foundation.authz.infrastructure.RoleRepository;
import com.foundation.authz.infrastructure.SelfManagedImportRepository;
import com.foundation.authz.infrastructure.SnapshotRepository;
import com.foundation.authz.infrastructure.SubsystemRepository;
import com.foundation.common.api.event.OutboxAppender;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** BAS-05/BAS-06 应用层单测用的内存仓储与桩（对齐 foundation-app-biz 的测试风格）。 */
final class TestDoubles {

    private TestDoubles() {
    }

    static final class InMemoryAppApi implements AppApi {

        private final Map<String, AppView> byCode = new LinkedHashMap<>();

        void register(String id, String code) {
            byCode.put(code, new AppView(id, code, code, null, null, "SUB", "user_1", "ENABLED", 0L));
        }

        @Override
        public List<AppView> list() {
            return List.copyOf(byCode.values());
        }

        @Override
        public Optional<AppView> find(String code) {
            return Optional.ofNullable(byCode.get(code));
        }

        @Override
        public String register(AppRegistrationCmd cmd) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(AppUpdateCmd cmd) {
            throw new UnsupportedOperationException();
        }
    }

    static final class StubManifestVersions implements AppManifestVersionApi {

        private final Map<String, Long> versions = new LinkedHashMap<>();

        @Override
        public long currentVersion(String appCode) {
            return versions.getOrDefault(appCode, 0L);
        }

        @Override
        public void advance(String appCode, long newVersion, String operator) {
            versions.put(appCode, newVersion);
        }
    }

    static final class InMemoryAppManifestRepository implements AppManifestRepository {

        final List<AppManifest> rows = new ArrayList<>();

        @Override
        public Optional<AppManifest> findByHash(String appId, String contentHash) {
            return rows.stream()
                    .filter(row -> row.appId().equals(appId) && row.contentHash().equals(contentHash))
                    .findFirst();
        }

        @Override
        public void insert(AppManifest manifest) {
            rows.add(manifest);
        }
    }

    static final class InMemoryMenuRepository implements MenuRepository {

        final Map<String, Menu> byId = new LinkedHashMap<>();

        @Override
        public List<Menu> listByApp(String appId) {
            return byId.values().stream().filter(menu -> menu.appId().equals(appId)).toList();
        }

        @Override
        public Optional<Menu> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public void insert(Menu menu) {
            byId.put(menu.id(), menu);
        }

        @Override
        public void updateFromManifest(Menu menu) {
            Menu existing = byId.get(menu.id());
            byId.put(menu.id(), new Menu(menu.id(), menu.appId(), menu.code(), existing.name(), menu.type(),
                    menu.parentId(), menu.routePath(), menu.component(), existing.icon(), existing.sort(),
                    existing.visible(), menu.permissionCode(), menu.status()));
        }

        @Override
        public void updateDisplay(Menu menu) {
            byId.put(menu.id(), menu);
        }

        @Override
        public void offline(String id) {
            Menu menu = byId.get(id);
            byId.put(id, new Menu(menu.id(), menu.appId(), menu.code(), menu.name(), menu.type(), menu.parentId(),
                    menu.routePath(), menu.component(), menu.icon(), menu.sort(), menu.visible(),
                    menu.permissionCode(), LifecycleStatus.OFFLINE));
        }
    }

    static final class InMemoryPermissionCodeRepository implements PermissionCodeRepository {

        final Map<String, PermissionCode> byId = new LinkedHashMap<>();

        @Override
        public List<PermissionCode> listByApp(String appId) {
            return byId.values().stream().filter(code -> code.appId().equals(appId)).toList();
        }

        @Override
        public List<PermissionCode> listActive() {
            return byId.values().stream().filter(PermissionCode::active).toList();
        }

        @Override
        public Optional<PermissionCode> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public void insert(PermissionCode permissionCode) {
            byId.put(permissionCode.id(), permissionCode);
        }

        @Override
        public void updateFromManifest(PermissionCode permissionCode) {
            PermissionCode existing = byId.get(permissionCode.id());
            byId.put(permissionCode.id(), new PermissionCode(permissionCode.id(), permissionCode.appId(),
                    permissionCode.code(), permissionCode.name(), permissionCode.riskLevel(),
                    permissionCode.status(), existing.firstSeenAt(), permissionCode.offlineAt()));
        }

        @Override
        public void offline(String id, Instant offlineAt) {
            PermissionCode existing = byId.get(id);
            byId.put(id, new PermissionCode(existing.id(), existing.appId(), existing.code(), existing.name(),
                    existing.riskLevel(), LifecycleStatus.OFFLINE, existing.firstSeenAt(), offlineAt));
        }
    }

    static final class InMemoryRoleRepository implements RoleRepository {

        final Map<String, Role> byCode = new LinkedHashMap<>();

        @Override
        public List<Role> list() {
            return List.copyOf(byCode.values());
        }

        @Override
        public Optional<Role> find(String code) {
            return Optional.ofNullable(byCode.get(code));
        }

        @Override
        public void insert(Role role) {
            byCode.put(role.code(), role);
        }

        @Override
        public void update(Role role) {
            byCode.put(role.code(), role);
        }

        @Override
        public void replacePermissions(String roleCode, Set<String> permissionCodes) {
            Role role = byCode.get(roleCode);
            byCode.put(roleCode, new Role(role.code(), role.name(), role.scope(), role.appId(), role.builtIn(),
                    role.dataScope(), permissionCodes));
        }

        @Override
        public void delete(String code) {
            byCode.remove(code);
        }
    }

    static final class InMemoryRoleMutexRepository implements RoleMutexRepository {

        final List<RoleMutex> pairs = new ArrayList<>();

        @Override
        public List<RoleMutex> all() {
            return List.copyOf(pairs);
        }
    }

    static final class InMemoryRoleGrantRepository implements RoleGrantRepository {

        final Map<String, RoleGrant> byId = new LinkedHashMap<>();

        @Override
        public List<RoleGrant> list(GrantSubjectType subjectType, String subjectId) {
            return byId.values().stream()
                    .filter(grant -> subjectType == null || grant.subjectType() == subjectType)
                    .filter(grant -> subjectId == null || grant.subjectId().equals(subjectId))
                    .toList();
        }

        @Override
        public Optional<RoleGrant> find(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public void insert(RoleGrant grant) {
            byId.put(grant.id(), grant);
        }

        @Override
        public void delete(String id) {
            byId.remove(id);
        }

        @Override
        public long countByRole(String roleCode) {
            return byId.values().stream().filter(grant -> grant.roleCode().equals(roleCode)).count();
        }
    }

    static final class StubSubjectMembership implements SubjectMembership {

        final Map<String, Set<String>> orgUnits = new LinkedHashMap<>();
        final Map<String, Set<String>> posts = new LinkedHashMap<>();
        final Map<String, Set<String>> descendants = new LinkedHashMap<>();

        @Override
        public Set<String> orgUnitsOf(String personId) {
            return orgUnits.getOrDefault(personId, Set.of());
        }

        @Override
        public Set<String> postsOf(String personId) {
            return posts.getOrDefault(personId, Set.of());
        }

        @Override
        public Set<String> descendantsOf(String orgUnitId) {
            return descendants.getOrDefault(orgUnitId, Set.of(orgUnitId));
        }
    }

    static final class RecordingOutbox implements OutboxAppender {

        final List<String> eventTypes = new ArrayList<>();
        final List<Map<String, Object>> payloads = new ArrayList<>();

        @Override
        public void append(String topic, String eventType, String aggregate, Map<String, Object> payload) {
            eventTypes.add(eventType);
            payloads.add(payload);
        }
    }

    static final class InMemoryDataDimensionRepository implements DataDimensionRepository {

        final Map<String, DataDimension> byId = new LinkedHashMap<>();

        @Override
        public List<DataDimension> list(String appCode) {
            return byId.values().stream()
                    .filter(dimension -> appCode == null || appCode.isBlank()
                            ? dimension.global()
                            : dimension.global() || appCode.equals(dimension.appCode()))
                    .toList();
        }

        @Override
        public List<DataDimension> listAll() {
            return List.copyOf(byId.values());
        }

        @Override
        public Optional<DataDimension> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<DataDimension> findByCode(String appCode, String code) {
            return byId.values().stream()
                    .filter(dimension -> dimension.code().equals(code))
                    .filter(dimension -> appCode == null || appCode.isBlank()
                            ? dimension.global()
                            : appCode.equals(dimension.appCode()))
                    .findFirst();
        }

        @Override
        public Optional<DataDimension> findByCodeAnywhere(String code) {
            return byId.values().stream()
                    .filter(dimension -> dimension.code().equals(code))
                    .min(Comparator.comparingInt(dimension -> dimension.global() ? 0 : 1));
        }

        @Override
        public void insert(DataDimension dimension) {
            byId.put(dimension.id(), dimension);
        }

        @Override
        public void update(DataDimension dimension) {
            byId.put(dimension.id(), dimension);
        }
    }

    static final class InMemoryDimensionValueRepository implements DimensionValueRepository {

        final Map<String, DimensionValue> byId = new LinkedHashMap<>();

        @Override
        public List<DimensionValue> listByDimension(String dimensionId) {
            return byId.values().stream().filter(value -> value.dimensionId().equals(dimensionId)).toList();
        }

        @Override
        public Optional<DimensionValue> findByCode(String dimensionId, String code) {
            return byId.values().stream()
                    .filter(value -> value.dimensionId().equals(dimensionId) && value.code().equals(code))
                    .findFirst();
        }

        @Override
        public void insert(DimensionValue value) {
            byId.put(value.id(), value);
        }

        @Override
        public void update(DimensionValue value) {
            byId.put(value.id(), value);
        }

        @Override
        public void deactivate(String dimensionId, String code) {
            findByCode(dimensionId, code).ifPresent(value -> byId.put(value.id(),
                    new DimensionValue(value.id(), value.dimensionId(), value.code(), value.name(),
                            value.parentId(), false)));
        }

        @Override
        public void deleteByDimension(String dimensionId) {
            byId.values().removeIf(value -> value.dimensionId().equals(dimensionId));
        }
    }

    static final class InMemoryRoleDataScopeRepository implements RoleDataScopeRepository {

        final Map<String, RoleDataScope> byRole = new LinkedHashMap<>();

        @Override
        public Optional<RoleDataScope> findByRole(String roleCode) {
            return Optional.ofNullable(byRole.get(roleCode));
        }

        @Override
        public void upsert(RoleDataScope scope) {
            byRole.put(scope.roleCode(), scope);
        }
    }

    static final class InMemoryRoleDimensionValueRepository implements RoleDimensionValueRepository {

        final List<RoleDimensionValue> rows = new ArrayList<>();

        @Override
        public List<RoleDimensionValue> listByRole(String roleCode) {
            return rows.stream().filter(value -> value.roleCode().equals(roleCode)).toList();
        }

        @Override
        public void deleteByRole(String roleCode) {
            rows.removeIf(value -> value.roleCode().equals(roleCode));
        }

        @Override
        public void insert(RoleDimensionValue value) {
            rows.add(value);
        }
    }

    static final class InMemoryPersonDimensionRepository implements PersonDimensionRepository {

        final List<PersonDimensionGrant> rows = new ArrayList<>();

        @Override
        public List<PersonDimensionGrant> listByPerson(String personId) {
            return rows.stream().filter(grant -> grant.personId().equals(personId)).toList();
        }

        @Override
        public List<PersonDimensionGrant> listByPersonAndDimension(String personId, String dimensionId) {
            return rows.stream()
                    .filter(grant -> grant.personId().equals(personId) && grant.dimensionId().equals(dimensionId))
                    .toList();
        }

        @Override
        public void insert(PersonDimensionGrant grant) {
            rows.add(grant);
        }

        @Override
        public void deleteByPersonAndDimension(String personId, String dimensionId) {
            rows.removeIf(grant -> grant.personId().equals(personId) && grant.dimensionId().equals(dimensionId));
        }
    }

    static final class InMemorySnapshotRepository implements SnapshotRepository {

        final Map<String, AuthzSnapshot> byKey = new LinkedHashMap<>();

        @Override
        public Optional<Long> version(String personId, String appCode) {
            return Optional.ofNullable(byKey.get(key(personId, appCode))).map(AuthzSnapshot::version);
        }

        @Override
        public Optional<AuthzSnapshot> find(String personId, String appCode) {
            return Optional.ofNullable(byKey.get(key(personId, appCode)));
        }

        @Override
        public List<String> listPersonsByApp(String appCode) {
            return byKey.values().stream().filter(snapshot -> snapshot.appCode().equals(appCode))
                    .map(AuthzSnapshot::personId).toList();
        }

        @Override
        public void upsert(AuthzSnapshot snapshot) {
            byKey.put(key(snapshot.personId(), snapshot.appCode()), snapshot);
        }

        private static String key(String personId, String appCode) {
            return personId + "@" + appCode;
        }
    }

    /** 数据范围桩：固定返回预设的解析结果（供 BAS-07 快照汇总测试）。 */
    static final class StubDataScopeApi implements DataScopeApi {

        DataScopeView view = new DataScopeView(ScopeView.of(List.of()), Map.of());

        @Override
        public void setRoleDataScope(String roleCode, RoleDataScopeCmd cmd) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RoleDataScopeView roleDataScope(String roleCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> grantPersonDimension(String personId, PersonDimensionCmd cmd) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void revokePersonDimension(String personId, String dimensionCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DataScopeView resolve(String personId, String appCode, Instant at) {
            return view;
        }
    }

    // ---------- BAS-27 子系统 / 模块 / 功能目录与功能开关 ----------

    /** 功能开关桩：按「作用域类型 + 作用域 ID」集合判定停用（不做链式继承）。 */
    static final class StubFeatureToggleApi implements FeatureToggleApi {

        final Set<String> disabledKeys = new LinkedHashSet<>();
        final Map<String, FeatureToggle> stored = new LinkedHashMap<>();

        void disable(String scopeType, String scopeId) {
            disabledKeys.add(key(scopeType, scopeId));
        }

        @Override
        public List<FeatureToggleView> list() {
            return stored.values().stream()
                    .map(toggle -> new FeatureToggleView(toggle.scopeType().name(), toggle.scopeId(),
                            toggle.enabled(), toggle.reason()))
                    .toList();
        }

        @Override
        public FeatureToggleView set(String scopeType, String scopeId, boolean enabled, String reason,
                                     String operator) {
            FeatureToggle toggle = new FeatureToggle("tog_" + scopeId, ToggleScopeType.of(scopeType), scopeId,
                    enabled, reason);
            stored.put(key(scopeType, scopeId), toggle);
            if (enabled) {
                disabledKeys.remove(key(scopeType, scopeId));
            } else {
                disabledKeys.add(key(scopeType, scopeId));
            }
            return new FeatureToggleView(toggle.scopeType().name(), scopeId, enabled, reason);
        }

        @Override
        public boolean disabled(String scopeType, String scopeId) {
            return disabledKeys.contains(key(scopeType, scopeId));
        }

        private static String key(String scopeType, String scopeId) {
            return scopeType + ":" + scopeId;
        }
    }

    static final class InMemorySubsystemRepository implements SubsystemRepository {

        final Map<String, Subsystem> byId = new LinkedHashMap<>();

        @Override
        public List<Subsystem> list() {
            return List.copyOf(byId.values());
        }

        @Override
        public Optional<Subsystem> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Subsystem> findByAppId(String appId) {
            return byId.values().stream().filter(subsystem -> subsystem.appId().equals(appId)).findFirst();
        }

        @Override
        public void insert(Subsystem subsystem) {
            byId.put(subsystem.id(), subsystem);
        }

        @Override
        public void updateAuthMode(String id, AuthMode authMode) {
            Subsystem existing = byId.get(id);
            byId.put(id, existing.withAuthMode(authMode));
        }
    }

    static final class InMemoryModuleRepository implements ModuleRepository {

        final Map<String, SubsystemModule> byId = new LinkedHashMap<>();

        @Override
        public List<SubsystemModule> listBySubsystem(String subsystemId) {
            return byId.values().stream()
                    .filter(module -> module.subsystemId().equals(subsystemId))
                    .sorted(Comparator.comparingInt(SubsystemModule::sort)
                            .thenComparing(SubsystemModule::code))
                    .toList();
        }

        @Override
        public Optional<SubsystemModule> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<SubsystemModule> findByCode(String subsystemId, String code) {
            return byId.values().stream()
                    .filter(module -> module.subsystemId().equals(subsystemId) && module.code().equals(code))
                    .findFirst();
        }

        @Override
        public void insert(SubsystemModule module) {
            byId.put(module.id(), module);
        }

        @Override
        public void update(SubsystemModule module) {
            byId.put(module.id(), module);
        }

        @Override
        public void offline(String id) {
            byId.put(id, byId.get(id).offline());
        }
    }

    static final class InMemoryFunctionRepository implements FunctionRepository {

        final Map<String, SubsystemFunction> byId = new LinkedHashMap<>();

        @Override
        public List<SubsystemFunction> listByModule(String moduleId) {
            return byId.values().stream()
                    .filter(function -> function.moduleId().equals(moduleId))
                    .sorted(Comparator.comparingInt(SubsystemFunction::sort)
                            .thenComparing(SubsystemFunction::code))
                    .toList();
        }

        @Override
        public List<SubsystemFunction> listByModules(java.util.Collection<String> moduleIds) {
            if (moduleIds == null || moduleIds.isEmpty()) {
                return List.of();
            }
            return byId.values().stream().filter(function -> moduleIds.contains(function.moduleId())).toList();
        }

        @Override
        public Optional<SubsystemFunction> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<SubsystemFunction> findByCode(String moduleId, String code) {
            return byId.values().stream()
                    .filter(function -> function.moduleId().equals(moduleId) && function.code().equals(code))
                    .findFirst();
        }

        @Override
        public void insert(SubsystemFunction function) {
            byId.put(function.id(), function);
        }

        @Override
        public void update(SubsystemFunction function) {
            byId.put(function.id(), function);
        }

        @Override
        public void offline(String id) {
            byId.put(id, byId.get(id).offline());
        }
    }

    static final class InMemoryFeatureToggleRepository implements FeatureToggleRepository {

        final Map<String, FeatureToggle> byId = new LinkedHashMap<>();

        @Override
        public List<FeatureToggle> list() {
            return List.copyOf(byId.values());
        }

        @Override
        public Optional<FeatureToggle> find(ToggleScopeType scopeType, String scopeId) {
            return byId.values().stream()
                    .filter(toggle -> toggle.scopeType() == scopeType && toggle.scopeId().equals(scopeId))
                    .findFirst();
        }

        @Override
        public Optional<FeatureToggle> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public void insert(FeatureToggle toggle) {
            byId.put(toggle.id(), toggle);
        }

        @Override
        public void update(FeatureToggle toggle) {
            byId.put(toggle.id(), toggle);
        }
    }

    static final class InMemoryOrgAdminScopeRepository implements OrgAdminScopeRepository {

        final Map<String, OrgAdminScope> byId = new LinkedHashMap<>();

        @Override
        public List<OrgAdminScope> list(String userId) {
            return byId.values().stream()
                    .filter(scope -> userId == null || scope.userId().equals(userId))
                    .toList();
        }

        @Override
        public Optional<OrgAdminScope> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<OrgAdminScope> findByUserAndOrgUnit(String userId, String orgUnitId) {
            return byId.values().stream()
                    .filter(scope -> scope.userId().equals(userId) && scope.orgUnitId().equals(orgUnitId))
                    .findFirst();
        }

        @Override
        public void insert(OrgAdminScope scope) {
            byId.put(scope.id(), scope);
        }

        @Override
        public void update(OrgAdminScope scope) {
            byId.put(scope.id(), scope);
        }
    }

    static final class InMemorySelfManagedImportRepository implements SelfManagedImportRepository {

        final Map<String, SelfManagedImport> batches = new LinkedHashMap<>();
        final Map<String, SelfManagedImportItem> items = new LinkedHashMap<>();

        @Override
        public Optional<SelfManagedImport> findById(String id) {
            return Optional.ofNullable(batches.get(id));
        }

        @Override
        public List<SelfManagedImport> listBySubsystem(String subsystemId) {
            return batches.values().stream()
                    .filter(batch -> batch.subsystemId().equals(subsystemId))
                    .toList();
        }

        @Override
        public void insert(SelfManagedImport batch) {
            batches.put(batch.id(), batch);
        }

        @Override
        public void update(SelfManagedImport batch) {
            batches.put(batch.id(), batch);
        }

        @Override
        public void insertItem(SelfManagedImportItem item) {
            items.put(item.id(), item);
        }

        @Override
        public List<SelfManagedImportItem> items(String batchId) {
            return items.values().stream().filter(item -> item.batchId().equals(batchId)).toList();
        }
    }
}
