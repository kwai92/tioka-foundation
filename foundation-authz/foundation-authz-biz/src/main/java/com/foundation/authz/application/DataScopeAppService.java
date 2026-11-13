package com.foundation.authz.application;

import com.foundation.app.api.AppApi;
import com.foundation.app.api.AppView;
import com.foundation.authz.api.DataScopeApi;
import com.foundation.authz.api.DataScopeView;
import com.foundation.authz.api.PersonDimensionCmd;
import com.foundation.authz.api.RoleDataScopeCmd;
import com.foundation.authz.api.RoleDataScopeView;
import com.foundation.authz.api.ScopeCmd;
import com.foundation.authz.api.ScopeView;
import com.foundation.authz.domain.AuthzIdPrefix;
import com.foundation.authz.domain.DataDimension;
import com.foundation.authz.domain.DataScopeResolution;
import com.foundation.authz.domain.DataScopeResolver;
import com.foundation.authz.domain.DeptScope;
import com.foundation.authz.domain.GrantSource;
import com.foundation.authz.domain.PersonDimensionGrant;
import com.foundation.authz.domain.Role;
import com.foundation.authz.domain.RoleDataScope;
import com.foundation.authz.domain.RoleDimensionValue;
import com.foundation.authz.domain.SubjectMembership;
import com.foundation.authz.infrastructure.DataDimensionRepository;
import com.foundation.authz.infrastructure.PersonDimensionRepository;
import com.foundation.authz.infrastructure.RoleDataScopeRepository;
import com.foundation.authz.infrastructure.RoleDimensionValueRepository;
import com.foundation.authz.infrastructure.RoleRepository;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据权限维护与解析（F-06 BAS-06 §接口 {@code /api/fdn}）。
 *
 * <p>只在 Foundation 定义与计算，不执行；合并规则见 {@link DataScopeResolver}。
 * 「手工授予」{@link GrantSource#MANUAL} 不被 {@code RULE}/{@code SYNC} 覆盖。
 */
public class DataScopeAppService implements DataScopeApi {

    private final RoleRepository roles;
    private final RoleDataScopeRepository roleScopes;
    private final RoleDimensionValueRepository roleDimensionValues;
    private final PersonDimensionRepository personDimensions;
    private final DataDimensionRepository dimensions;
    private final EffectiveRoleResolver effectiveRoles;
    private final SubjectMembership membership;
    private final DataScopeResolver resolver;
    private final AppApi apps;

    public DataScopeAppService(RoleRepository roles, RoleDataScopeRepository roleScopes,
                               RoleDimensionValueRepository roleDimensionValues,
                               PersonDimensionRepository personDimensions,
                               DataDimensionRepository dimensions,
                               EffectiveRoleResolver effectiveRoles, SubjectMembership membership,
                               DataScopeResolver resolver, AppApi apps) {
        this.roles = roles;
        this.roleScopes = roleScopes;
        this.roleDimensionValues = roleDimensionValues;
        this.personDimensions = personDimensions;
        this.dimensions = dimensions;
        this.effectiveRoles = effectiveRoles;
        this.membership = membership;
        this.resolver = resolver;
        this.apps = apps;
    }

    @Override
    @Transactional
    public void setRoleDataScope(String roleCode, RoleDataScopeCmd cmd) {
        Role role = roles.find(roleCode)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "角色不存在：" + roleCode));
        String appCode = appCodeOf(role);

        roleDimensionValues.deleteByRole(roleCode);
        for (Map.Entry<String, ScopeCmd> entry : cmd.dimensions().entrySet()) {
            DataDimension dimension = dimensions.findByCode(appCode, entry.getKey())
                    .or(() -> dimensions.findByCode(null, entry.getKey()))
                    .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "维度不存在：" + entry.getKey()));
            ScopeCmd scope = entry.getValue();
            if (scope.all()) {
                roleDimensionValues.insert(new RoleDimensionValue(Ids.newId(AuthzIdPrefix.ROLE_DIMENSION_VALUE),
                        roleCode, dimension.id(), null, true));
                continue;
            }
            for (String value : scope.values()) {
                roleDimensionValues.insert(new RoleDimensionValue(Ids.newId(AuthzIdPrefix.ROLE_DIMENSION_VALUE),
                        roleCode, dimension.id(), value, false));
            }
        }
        roleScopes.upsert(new RoleDataScope(Ids.newId(AuthzIdPrefix.ROLE_DATA_SCOPE), roleCode,
                DeptScope.of(cmd.deptScope()), cmd.deptUnits()));
    }

    @Override
    public RoleDataScopeView roleDataScope(String roleCode) {
        roles.find(roleCode).orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "角色不存在：" + roleCode));
        Map<String, String> codeById = dimensionCodeById();

        Map<String, List<RoleDimensionValue>> grouped = roleDimensionValues.listByRole(roleCode).stream()
                .collect(Collectors.groupingBy(RoleDimensionValue::dimensionId, LinkedHashMap::new, Collectors.toList()));
        Map<String, ScopeView> dimensions = new LinkedHashMap<>();
        for (Map.Entry<String, List<RoleDimensionValue>> entry : grouped.entrySet()) {
            String code = codeById.get(entry.getKey());
            if (code == null) {
                continue;
            }
            dimensions.put(code, toScopeView(entry.getValue()));
        }

        Optional<RoleDataScope> scope = roleScopes.findByRole(roleCode);
        return new RoleDataScopeView(roleCode,
                scope.map(value -> value.deptScope().name()).orElse(DeptScope.SELF.name()),
                scope.map(RoleDataScope::deptUnits).orElse(List.of()),
                dimensions);
    }

    @Override
    @Transactional
    public List<String> grantPersonDimension(String personId, PersonDimensionCmd cmd) {
        DataDimension dimension = dimensions.findByCodeAnywhere(cmd.dimensionCode())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "维度不存在：" + cmd.dimensionCode()));
        GrantSource source = GrantSource.of(cmd.source());

        boolean manualKept = personDimensions.listByPersonAndDimension(personId, dimension.id()).stream()
                .anyMatch(grant -> grant.source() == GrantSource.MANUAL);
        if (manualKept && source != GrantSource.MANUAL) {
            throw new BizException(ErrorCode.CONFLICT, "手工维度授予不可被规则或同步覆盖：" + cmd.dimensionCode());
        }

        List<String> ids = new ArrayList<>();
        if (cmd.all()) {
            ids.add(insertPersonDimension(personId, dimension.id(), null, true, source));
            return ids;
        }
        for (String value : cmd.values()) {
            ids.add(insertPersonDimension(personId, dimension.id(), value, false, source));
        }
        return ids;
    }

    @Override
    @Transactional
    public void revokePersonDimension(String personId, String dimensionCode) {
        DataDimension dimension = dimensions.findByCodeAnywhere(dimensionCode)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "维度不存在：" + dimensionCode));
        personDimensions.deleteByPersonAndDimension(personId, dimension.id());
    }

    @Override
    public DataScopeView resolve(String personId, String appCode, Instant at) {
        Set<String> effective = effectiveRoles.resolve(personId, appCode, at);

        List<RoleDataScope> scopes = effective.stream()
                .map(roleScopes::findByRole).flatMap(Optional::stream).toList();
        List<RoleDimensionValue> roleValues = effective.stream()
                .flatMap(roleCode -> roleDimensionValues.listByRole(roleCode).stream()).toList();
        List<PersonDimensionGrant> grants = personDimensions.listByPerson(personId);

        DataScopeResolution resolution = resolver.resolve(personId, effective, scopes, roleValues,
                grants, dimensionCodeById(), membership);

        Map<String, ScopeView> dimensionViews = new LinkedHashMap<>();
        resolution.dimensions().forEach((code, scope) -> dimensionViews.put(code, toScopeView(scope)));
        return new DataScopeView(toScopeView(resolution.deptScope()), dimensionViews);
    }

    private String insertPersonDimension(String personId, String dimensionId, String valueCode,
                                         boolean allValues, GrantSource source) {
        String id = Ids.newId(AuthzIdPrefix.PERSON_DIMENSION);
        personDimensions.insert(new PersonDimensionGrant(id, personId, dimensionId, valueCode, allValues, source));
        return id;
    }

    private Map<String, String> dimensionCodeById() {
        Map<String, String> map = new LinkedHashMap<>();
        for (DataDimension dimension : dimensions.listAll()) {
            map.put(dimension.id(), dimension.code());
        }
        return map;
    }

    private String appCodeOf(Role role) {
        if (role.appId() == null) {
            return null;
        }
        return apps.list().stream()
                .filter(app -> app.id().equals(role.appId()))
                .map(AppView::code)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "应用不存在：" + role.appId()));
    }

    private static ScopeView toScopeView(List<RoleDimensionValue> values) {
        boolean all = values.stream().anyMatch(RoleDimensionValue::allValues);
        if (all) {
            return ScopeView.ofAll();
        }
        List<String> codes = values.stream().map(RoleDimensionValue::valueCode)
                .filter(code -> code != null && !code.isBlank()).toList();
        return ScopeView.of(codes);
    }

    private static ScopeView toScopeView(DataScopeResolution.Scope scope) {
        return scope.all() ? ScopeView.ofAll() : ScopeView.of(scope.values());
    }
}
