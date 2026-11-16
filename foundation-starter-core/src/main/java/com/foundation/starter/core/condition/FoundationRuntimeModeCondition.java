package com.foundation.starter.core.condition;

import com.foundation.starter.core.runtime.FoundationRuntimeMode;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@link ConditionalOnFoundationRuntimeMode} 的判定实现。
 *
 * <p>直接读 {@code Environment}，不依赖任何配置 Bean 已创建（条件求值早于 Bean 实例化）。
 * 注解属性在不同元数据实现下可能返回枚举数组或字符串数组，这里统一按名称归一化后比较。
 */
public class FoundationRuntimeModeCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(
                ConditionalOnFoundationRuntimeMode.class.getName());
        if (attributes == null) {
            return false;
        }
        Set<String> expected = expectedModes(attributes.get("value"));
        if (expected.isEmpty()) {
            return false;
        }
        FoundationRuntimeMode actual = FoundationRuntimeMode.from(context.getEnvironment());
        return expected.contains(actual.name());
    }

    private static Set<String> expectedModes(Object raw) {
        Set<String> result = new HashSet<>();
        if (raw instanceof Object[] array) {
            for (Object item : array) {
                result.add(normalize(item));
            }
        } else if (raw != null) {
            result.add(normalize(raw));
        }
        return result;
    }

    /** 枚举实例、枚举名、连字符写法统一成大写下划线形式。 */
    private static String normalize(Object item) {
        if (item instanceof Enum<?> value) {
            return value.name().toUpperCase(Locale.ROOT).replace('-', '_');
        }
        return String.valueOf(item).trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
