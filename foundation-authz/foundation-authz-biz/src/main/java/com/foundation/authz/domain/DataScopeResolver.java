package com.foundation.authz.domain;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据范围合并（F-06 BAS-06 §合并规则）。
 *
 * <p>规则：跨角色取并集（OR）；同一角色内部门范围与各维度为 AND（由角色内条件块表达），
 * 对外给出去重后的并集结果；「全部」哨兵在并集中不被抹掉。
 */
public class DataScopeResolver {

    /**
     * 计算某人员的数据范围。
     *
     * @param personId            人员 ID
     * @param effectiveRoleCodes  有效角色集合（BAS-05 计算）
     * @param roleScopes          角色数据范围（含部门档位）
     * @param roleDimensionValues 角色维度取值
     * @param personGrants        人员维度授予
     * @param dimensionCodeById   维度 ID → 维度编码
     * @param membership          人员组织归属端口（用于 DEPT / DEPT_AND_SUB 展开）
     */
    public DataScopeResolution resolve(String personId,
                                       Set<String> effectiveRoleCodes,
                                       List<RoleDataScope> roleScopes,
                                       List<RoleDimensionValue> roleDimensionValues,
                                       List<PersonDimensionGrant> personGrants,
                                       Map<String, String> dimensionCodeById,
                                       SubjectMembership membership) {
        DataScopeResolution.Scope dept = resolveDept(personId, effectiveRoleCodes, roleScopes, membership);
        Map<String, DataScopeResolution.Scope> dimensions = resolveDimensions(effectiveRoleCodes,
                roleDimensionValues, personGrants, dimensionCodeById);
        return new DataScopeResolution(dept, dimensions);
    }

    private static DataScopeResolution.Scope resolveDept(String personId,
                                                         Set<String> effectiveRoleCodes,
                                                         List<RoleDataScope> roleScopes,
                                                         SubjectMembership membership) {
        boolean all = false;
        Set<String> units = new LinkedHashSet<>();
        for (RoleDataScope scope : roleScopes) {
            if (!effectiveRoleCodes.contains(scope.roleCode())) {
                continue;
            }
            switch (scope.deptScope()) {
                case ALL -> all = true;
                case CUSTOM -> units.addAll(scope.deptUnits());
                case DEPT -> units.addAll(membership.orgUnitsOf(personId));
                case DEPT_AND_SUB -> expandDescendants(personId, membership, units);
                case SELF -> {
                    // 部门集合为空：按 owner / 经办人判本人
                }
            }
        }
        return all ? DataScopeResolution.Scope.ofAll() : DataScopeResolution.Scope.of(units);
    }

    private static void expandDescendants(String personId, SubjectMembership membership, Set<String> units) {
        for (String orgUnitId : membership.orgUnitsOf(personId)) {
            units.addAll(membership.descendantsOf(orgUnitId));
        }
    }

    private static Map<String, DataScopeResolution.Scope> resolveDimensions(
            Set<String> effectiveRoleCodes,
            List<RoleDimensionValue> roleDimensionValues,
            List<PersonDimensionGrant> personGrants,
            Map<String, String> dimensionCodeById) {

        Map<String, Set<String>> valuesByDimension = new LinkedHashMap<>();
        Set<String> allDimensions = new LinkedHashSet<>();

        for (RoleDimensionValue value : roleDimensionValues) {
            if (!effectiveRoleCodes.contains(value.roleCode())) {
                continue;
            }
            accumulate(dimensionCodeById, value.dimensionId(), value.valueCode(), value.allValues(),
                    valuesByDimension, allDimensions);
        }
        for (PersonDimensionGrant grant : personGrants) {
            accumulate(dimensionCodeById, grant.dimensionId(), grant.valueCode(), grant.allValues(),
                    valuesByDimension, allDimensions);
        }

        Map<String, DataScopeResolution.Scope> result = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : valuesByDimension.entrySet()) {
            result.put(entry.getKey(), allDimensions.contains(entry.getKey())
                    ? DataScopeResolution.Scope.ofAll()
                    : DataScopeResolution.Scope.of(entry.getValue()));
        }
        for (String dimensionCode : allDimensions) {
            result.putIfAbsent(dimensionCode, DataScopeResolution.Scope.ofAll());
        }
        return result;
    }

    private static void accumulate(Map<String, String> dimensionCodeById,
                                   String dimensionId,
                                   String valueCode,
                                   boolean allValues,
                                   Map<String, Set<String>> valuesByDimension,
                                   Set<String> allDimensions) {
        String dimensionCode = dimensionCodeById.get(dimensionId);
        if (dimensionCode == null) {
            return;
        }
        if (allValues) {
            allDimensions.add(dimensionCode);
            return;
        }
        if (valueCode != null && !valueCode.isBlank()) {
            valuesByDimension.computeIfAbsent(dimensionCode, key -> new LinkedHashSet<>()).add(valueCode);
        }
    }
}
