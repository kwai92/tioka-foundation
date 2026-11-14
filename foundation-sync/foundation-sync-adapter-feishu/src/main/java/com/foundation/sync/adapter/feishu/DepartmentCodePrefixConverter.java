package com.foundation.sync.adapter.feishu;

import com.foundation.sync.spi.FieldConverter;

/**
 * 部门编码前缀转换函数（F-08 BAS-12）：{@code department-code-prefix}。
 *
 * <p>把来源部门编码统一加前缀（如 {@code D01 → FIN-D01}）；已带前缀的幂等不重复添加。
 */
public class DepartmentCodePrefixConverter implements FieldConverter {

    /** 转换函数名。 */
    public static final String NAME = "department-code-prefix";

    private final String prefix;

    public DepartmentCodePrefixConverter(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Object convert(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = String.valueOf(rawValue).trim();
        if (value.isEmpty()) {
            return value;
        }
        if (!prefix.isEmpty() && value.startsWith(prefix)) {
            return value;
        }
        return prefix + value;
    }
}
