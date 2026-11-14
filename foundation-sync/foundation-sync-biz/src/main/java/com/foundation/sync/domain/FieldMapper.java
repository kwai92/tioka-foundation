package com.foundation.sync.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.sync.spi.FieldConverter;
import com.foundation.sync.spi.SyncObjectType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 字段映射（F-08 BAS-11 §8、§12）：按 {@code fdn_sync_field_mapping} 把外部字段翻译成内部字段。
 *
 * <p>规则：外部字段缺失时填默认值；仍缺失且必填则报错；指定了 {@code transform} 时经
 * {@link FieldConverter} 转换（未注册的转换函数名视为配置错误）。
 */
public class FieldMapper {

    private final Map<String, FieldConverter> converters;

    public FieldMapper(Collection<FieldConverter> converters) {
        this.converters = (converters == null ? List.<FieldConverter>of() : List.copyOf(converters))
                .stream()
                .collect(Collectors.toUnmodifiableMap(FieldConverter::name, Function.identity(), (a, b) -> a));
    }

    /**
     * 映射一条外部记录。
     *
     * @param objectType     对象类型
     * @param mappings       该来源的全部映射（内部按 objectType 过滤）
     * @param externalFields 外部字段取值
     * @return 内部字段取值（保持映射声明顺序）
     */
    public Map<String, Object> map(SyncObjectType objectType, List<FieldMapping> mappings,
                                   Map<String, Object> externalFields) {
        Map<String, Object> source = externalFields == null ? Map.of() : externalFields;
        Map<String, Object> mapped = new LinkedHashMap<>();
        if (mappings == null) {
            return mapped;
        }
        for (FieldMapping mapping : mappings) {
            if (mapping.objectType() != objectType) {
                continue;
            }
            Object raw = source.get(mapping.externalField());
            if (raw == null || (raw instanceof String str && str.isBlank())) {
                if (mapping.defaultValue() != null) {
                    raw = mapping.defaultValue();
                } else if (mapping.required()) {
                    throw new BizException(ErrorCode.BAD_REQUEST,
                            "缺少必填外部字段: " + mapping.externalField());
                } else {
                    continue;
                }
            }
            mapped.put(mapping.internalField(), convert(mapping.transform(), raw));
        }
        return mapped;
    }

    /** 把外部记录整批映射为内部记录。 */
    public MappedRecord map(com.foundation.sync.spi.ExternalRecord record, List<FieldMapping> mappings) {
        return new MappedRecord(record.objectType(), record.externalId(),
                map(record.objectType(), mappings, record.fields()),
                record.parentExternalId(), record.deleted());
    }

    private Object convert(String transform, Object raw) {
        if (transform == null || transform.isBlank()) {
            return raw;
        }
        FieldConverter converter = converters.get(transform);
        if (converter == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "未知字段转换函数: " + transform);
        }
        return converter.convert(raw);
    }
}
