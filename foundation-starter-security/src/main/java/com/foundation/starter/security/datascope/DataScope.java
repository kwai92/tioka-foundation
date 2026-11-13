package com.foundation.starter.security.datascope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据范围声明（F-07 §5）：声明本查询的部门字段与维度字段，starter 按快照生成过滤条件。
 *
 * <p>用法一（注解 + 持久层条件生成）：标在查询方法或其参数上，配合
 * {@link DataScopeFilterProvider} 生成 SQL 片段与参数；
 * 用法二（直接读取快照取值集合）：从 {@code AuthzSnapshot} 的 {@code deptScope} / {@code dimensions}
 * 自行判断，适合复杂业务规则。
 *
 * <p>与 Tioka 既有空间行级过滤**并存、互不替代**，两者条件取交集（S15 §11.4）。
 */
@Target({ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /** 部门字段列名。 */
    String column() default "dept_code";

    /** 维度编码（如 {@code region}）；空表示只用部门范围。 */
    String dimension() default "";

    /** 维度字段列名。 */
    String dimensionColumn() default "";
}
