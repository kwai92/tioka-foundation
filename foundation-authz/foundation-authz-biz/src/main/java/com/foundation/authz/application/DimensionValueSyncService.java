package com.foundation.authz.application;

import com.foundation.authz.api.DimensionValueSyncCmd;
import com.foundation.authz.api.DimensionValueSyncResult;
import com.foundation.authz.domain.AuthzIdPrefix;
import com.foundation.authz.domain.DimensionValue;
import com.foundation.authz.infrastructure.DimensionValueRepository;
import com.foundation.common.id.Ids;

import java.util.Objects;
import java.util.Optional;

/**
 * 维度值增量维护（F-06 BAS-06 §接口 {@code PUT .../values:sync}）。
 *
 * <p>幂等：同编码重复上报且字段无变化不计入 {@code updated}；停用保留行（{@code active = false}），
 * 重新上报则复位为生效。
 */
public class DimensionValueSyncService {

    private final DimensionValueRepository values;

    public DimensionValueSyncService(DimensionValueRepository values) {
        this.values = values;
    }

    /** 对指定维度执行增量维护。 */
    public DimensionValueSyncResult sync(String dimensionId, DimensionValueSyncCmd cmd) {
        int created = 0;
        int updated = 0;
        int deactivated = 0;

        for (DimensionValueSyncCmd.ValueUpsert upsert : cmd.upserts()) {
            String parentId = resolveParentId(dimensionId, upsert.parentCode());
            Optional<DimensionValue> existing = values.findByCode(dimensionId, upsert.code());
            if (existing.isEmpty()) {
                values.insert(new DimensionValue(Ids.newId(AuthzIdPrefix.DIMENSION_VALUE), dimensionId,
                        upsert.code(), upsert.name(), parentId, true));
                created++;
                continue;
            }
            DimensionValue current = existing.get();
            boolean changed = !Objects.equals(current.name(), upsert.name())
                    || !Objects.equals(current.parentId(), parentId)
                    || !current.active();
            if (changed) {
                values.update(new DimensionValue(current.id(), dimensionId, current.code(),
                        upsert.name(), parentId, true));
                updated++;
            }
        }

        for (String code : cmd.deactivations()) {
            Optional<DimensionValue> existing = values.findByCode(dimensionId, code);
            if (existing.isPresent() && existing.get().active()) {
                values.deactivate(dimensionId, code);
                deactivated++;
            }
        }
        return new DimensionValueSyncResult(created, updated, deactivated);
    }

    private String resolveParentId(String dimensionId, String parentCode) {
        if (parentCode == null || parentCode.isBlank()) {
            return null;
        }
        return values.findByCode(dimensionId, parentCode).map(DimensionValue::id).orElse(null);
    }
}
