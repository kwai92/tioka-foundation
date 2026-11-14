package com.foundation.sync.spi;

/**
 * 字段转换函数扩展点（F-08 BAS-11 §2）。
 *
 * <p>函数名被 {@code fdn_sync_field_mapping.transform} 引用；由适配器插件注册实现
 * （如 {@code department-code-prefix} / {@code date-normalize} / {@code enum-normalize}）。
 */
public interface FieldConverter {

    /** 转换函数名（映射表 {@code transform} 列取值）。 */
    String name();

    /** 把来源原始值转换为内部字段值；入参可为 {@code null}。 */
    Object convert(Object rawValue);
}
