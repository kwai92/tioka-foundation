package com.foundation.sync.infrastructure;

import com.foundation.sync.config.SyncProperties;
import com.foundation.sync.spi.SyncSourceAdapter;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 同步来源适配器注册表（F-08 BAS-11 §12、§13，ADR-078 第 8 条）。
 *
 * <p>把容器发现的 {@link SyncSourceAdapter} 按 {@code sourceType()} 建索引。按
 * {@code foundation.sync.adapters} 过滤：为空表示装载全部已发现适配器，非空时只保留名单内的
 * 来源类型（名单外的适配器即使被容器发现也不参与编排）。索引与查找均大小写不敏感。
 */
public class AdapterRegistry {

    private final Map<String, SyncSourceAdapter> adapters;

    public AdapterRegistry(Collection<SyncSourceAdapter> discovered, SyncProperties properties) {
        List<String> allow = properties == null ? List.of() : properties.getAdapters();
        Map<String, SyncSourceAdapter> index = new LinkedHashMap<>();
        if (discovered != null) {
            for (SyncSourceAdapter adapter : discovered) {
                String sourceType = normalize(adapter.sourceType());
                if (sourceType == null || (!allow.isEmpty() && !containsIgnoreCase(allow, adapter.sourceType()))) {
                    continue;
                }
                SyncSourceAdapter previous = index.put(sourceType, adapter);
                if (previous != null) {
                    throw new IllegalStateException("重复注册的同步来源适配器：" + adapter.sourceType());
                }
            }
        }
        this.adapters = Collections.unmodifiableMap(index);
    }

    /** 按来源类型取适配器；未注册（或不在装载名单内）返回空。 */
    public Optional<SyncSourceAdapter> find(String sourceType) {
        String key = normalize(sourceType);
        return key == null ? Optional.empty() : Optional.ofNullable(adapters.get(key));
    }

    /** 是否已装载某来源类型的适配器。 */
    public boolean contains(String sourceType) {
        return find(sourceType).isPresent();
    }

    /** 全部已装载适配器（按来源类型排序稳定）。 */
    public Collection<SyncSourceAdapter> all() {
        return adapters.values();
    }

    private static boolean containsIgnoreCase(List<String> allow, String sourceType) {
        String key = normalize(sourceType);
        if (key == null) {
            return false;
        }
        for (String candidate : allow) {
            if (key.equals(normalize(candidate))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
