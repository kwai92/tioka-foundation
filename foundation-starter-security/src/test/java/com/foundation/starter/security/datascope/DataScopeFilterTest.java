package com.foundation.starter.security.datascope;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.starter.security.snapshot.AuthzSnapshot;
import com.foundation.starter.security.snapshot.DeptScope;
import com.foundation.starter.security.snapshot.DimensionScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** 数据范围条件生成（F-07 §5、§15）：五档归约、「全部」→ {@code 1=1}、部门与维度 AND、默认拒绝。 */
class DataScopeFilterTest {

    private static final String APP = "tioka";
    private static final String PERSON = "per_1";

    private final DefaultDataScopeFilterProvider provider = new DefaultDataScopeFilterProvider();

    @Test
    void allGeneratesPassThroughClause() {
        DataScopeFilter filter = provider.filter("dept_code", null, null,
                snapshot(DeptScope.ofAll(), Map.of()));

        assertThat(filter.all()).isTrue();
        assertThat(filter.sql()).isEqualTo("1=1");
        assertThat(filter.params()).isEmpty();
    }

    @Test
    void fiveLevelsReduceToAllOrValueSet() {
        // ALL → 1=1；CUSTOM / DEPT / DEPT_AND_SUB / SELF 在快照里已归约为展开后的组织单元集合
        List<String> customLevels = List.of("ou_custom", "ou_dept", "ou_dept_sub", "ou_self");

        for (String value : customLevels) {
            DataScopeFilter filter = provider.filter("dept_code", null, null,
                    snapshot(new DeptScope(false, Set.of(value)), Map.of()));

            assertThat(filter.all()).isFalse();
            assertThat(filter.sql()).isEqualTo("dept_code IN (:ds_dept_code_0)");
            assertThat(filter.params()).containsEntry("ds_dept_code_0", value);
        }
    }

    @Test
    void valueSetGeneratesInClauseWithNamedParams() {
        DataScopeFilter filter = provider.filter("dept_code", null, null,
                snapshot(new DeptScope(false, Set.of("ou_1", "ou_2")), Map.of()));

        assertThat(filter.all()).isFalse();
        assertThat(filter.sql()).matches("dept_code IN \\(:ds_dept_code_0, :ds_dept_code_1\\)");
        assertThat(filter.params()).hasSize(2);
        assertThat(filter.params().values()).containsExactlyInAnyOrder("ou_1", "ou_2");
    }

    @Test
    void departmentAndDimensionAreIntersectedByAnd() {
        AuthzSnapshot snapshot = snapshot(new DeptScope(false, Set.of("ou_1")),
                Map.of("region", new DimensionScope(false, Set.of("east", "west"))));

        DataScopeFilter filter = provider.filter("dept_code", "region", "region_code", snapshot);

        assertThat(filter.sql()).startsWith("dept_code IN (:ds_dept_code_0) AND region_code IN (");
        assertThat(filter.sql()).contains(":ds_region_code_0").contains(":ds_region_code_1");
        assertThat(filter.params()).containsEntry("ds_dept_code_0", "ou_1");
        assertThat(filter.params().values()).containsExactlyInAnyOrder("ou_1", "east", "west");
    }

    @Test
    void dimensionAllAddsNoDimensionClause() {
        AuthzSnapshot snapshot = snapshot(new DeptScope(false, Set.of("ou_1")),
                Map.of("region", DimensionScope.ofAll()));

        DataScopeFilter filter = provider.filter("dept_code", "region", "region_code", snapshot);

        assertThat(filter.sql()).isEqualTo("dept_code IN (:ds_dept_code_0)");
    }

    @Test
    void missingDimensionDenies() {
        AuthzSnapshot snapshot = snapshot(new DeptScope(false, Set.of("ou_1")), Map.of());

        DataScopeFilter filter = provider.filter("dept_code", "region", "region_code", snapshot);

        assertThat(filter.sql()).contains("1=0");
    }

    @Test
    void emptyDepartmentScopeDenies() {
        DataScopeFilter filter = provider.filter("dept_code", null, null,
                snapshot(new DeptScope(false, Set.of()), Map.of()));

        assertThat(filter.all()).isFalse();
        assertThat(filter.sql()).isEqualTo("1=0");
    }

    @Test
    void missingSnapshotDenies() {
        Throwable thrown = catchThrowable(() -> provider.filter("dept_code", null, null, null));

        assertThat(thrown).isInstanceOf(BizException.class);
        assertThat(((BizException) thrown).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    private static AuthzSnapshot snapshot(DeptScope deptScope, Map<String, DimensionScope> dimensions) {
        return new AuthzSnapshot(PERSON, APP, 1L, Set.of(), Set.of(),
                List.of(), deptScope, dimensions, null);
    }
}
