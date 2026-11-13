package com.foundation.authz.application;

import com.foundation.authz.api.DimensionValueSyncCmd;
import com.foundation.authz.api.DimensionValueSyncResult;
import com.foundation.authz.domain.DimensionValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 维度值增量维护（F-06 BAS-06 §接口 {@code values:sync}）：幂等、停用保留、重新上报复位。 */
class DimensionValueSyncServiceTest {

    private static final String DIMENSION = "fdd_region";

    private final TestDoubles.InMemoryDimensionValueRepository values =
            new TestDoubles.InMemoryDimensionValueRepository();
    private final DimensionValueSyncService service = new DimensionValueSyncService(values);

    @Test
    void createsThenIgnoresUnchangedReport() {
        DimensionValueSyncResult created = service.sync(DIMENSION, upsert("east", "华东", null));
        assertThat(created.created()).isEqualTo(1);
        assertThat(created.updated()).isZero();

        DimensionValueSyncResult repeated = service.sync(DIMENSION, upsert("east", "华东", null));
        assertThat(repeated.created()).isZero();
        assertThat(repeated.updated()).isZero();
    }

    @Test
    void updatesWhenFieldsChange() {
        service.sync(DIMENSION, upsert("east", "华东", null));

        DimensionValueSyncResult result = service.sync(DIMENSION, upsert("east", "华东区", null));

        assertThat(result.updated()).isEqualTo(1);
        assertThat(values.findByCode(DIMENSION, "east").orElseThrow().name()).isEqualTo("华东区");
    }

    @Test
    void deactivatesAndKeepsRowThenReactivatesOnUpsert() {
        service.sync(DIMENSION, upsert("east", "华东", null));

        DimensionValueSyncResult deactivated = service.sync(DIMENSION,
                new DimensionValueSyncCmd(List.of(), List.of("east")));
        assertThat(deactivated.deactivated()).isEqualTo(1);
        DimensionValue stopped = values.findByCode(DIMENSION, "east").orElseThrow();
        assertThat(stopped.active()).isFalse();
        assertThat(values.listByDimension(DIMENSION)).hasSize(1);

        DimensionValueSyncResult reactivated = service.sync(DIMENSION, upsert("east", "华东", null));
        assertThat(reactivated.updated()).isEqualTo(1);
        assertThat(values.findByCode(DIMENSION, "east").orElseThrow().active()).isTrue();
    }

    @Test
    void resolvesParentCodeToParentId() {
        service.sync(DIMENSION, upsert("east", "华东", null));
        service.sync(DIMENSION, upsert("shanghai", "上海", "east"));

        String parentId = values.findByCode(DIMENSION, "east").orElseThrow().id();
        assertThat(values.findByCode(DIMENSION, "shanghai").orElseThrow().parentId()).isEqualTo(parentId);
    }

    private static DimensionValueSyncCmd upsert(String code, String name, String parentCode) {
        return new DimensionValueSyncCmd(
                List.of(new DimensionValueSyncCmd.ValueUpsert(code, name, parentCode)), List.of());
    }
}
