package com.foundation.sync.spi;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 来源能力矩阵（F-08 BAS-11 §2、§3，ADR-078 第 2 条）。
 *
 * <p>引擎据此决定生成哪些差异、走哪种增量；能力缺失时降级并在同步报告里标注「能力缺失」，
 * 不静默跳过（{@code fdn_sync_task.capability_missing}）。
 *
 * @param objects                 支持的对象类型
 * @param incrementalModes        支持的增量方式
 * @param providesExternalIdentity 是否提供外部身份（{@code open_id} / {@code objectGUID} 等）
 * @param supportsFieldConversion 是否支持字段转换函数（{@link FieldConverter}）
 */
public record SourceCapabilities(Set<SyncObjectType> objects,
                                 Set<IncrementalMode> incrementalModes,
                                 boolean providesExternalIdentity,
                                 boolean supportsFieldConversion) {

    public SourceCapabilities {
        objects = objects == null ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(
                objects.isEmpty() ? EnumSet.noneOf(SyncObjectType.class) : EnumSet.copyOf(objects)));
        incrementalModes = incrementalModes == null ? Collections.emptySet()
                : Collections.unmodifiableSet(incrementalModes.isEmpty()
                ? EnumSet.noneOf(IncrementalMode.class) : EnumSet.copyOf(incrementalModes));
    }

    /** 空能力（未声明任何对象）。 */
    public static SourceCapabilities none() {
        return new SourceCapabilities(EnumSet.noneOf(SyncObjectType.class),
                EnumSet.noneOf(IncrementalMode.class), false, false);
    }

    /** 是否支持某对象。 */
    public boolean supports(SyncObjectType objectType) {
        return objects.contains(objectType);
    }

    /** 是否具备任一真正的增量方式。 */
    public boolean supportsIncremental() {
        return incrementalModes.stream().anyMatch(IncrementalMode::supportsIncremental);
    }
}
