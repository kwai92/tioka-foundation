package com.foundation.{app}.example.application;

import com.foundation.{app}.example.api.ExampleApi;
import com.foundation.{app}.example.api.ExampleView;
import com.foundation.{app}.example.domain.Example;
import com.foundation.starter.security.principal.PrincipalHolder;

/**
 * 示例应用服务（BAS-25 §分层：{@code application} 编排用例，实现 {@code -api} 契约）。
 *
 * <p>演示读取当前操作者：{@link PrincipalHolder#current()} 取请求线程内的 {@code Principal}；
 * 后台任务需先用 {@code PrincipalHolder.runAs} 恢复身份。
 */
public class ExampleService implements ExampleApi {

    @Override
    public ExampleView get(String id) {
        // 演示：当前操作者（未登录返回 anonymous）
        String operator = PrincipalHolder.current()
                .map(principal -> principal.userId())
                .orElse("anonymous");
        Example example = new Example(id, "example-of-" + operator);
        return toView(example);
    }

    @Override
    public ExampleView create(String name) {
        // 示例：主键形态 {前缀}_{ulid}；真实实现改用 foundation-common-api 的 Ids
        String id = "ex_" + Long.toUnsignedString(System.nanoTime(), 36);
        return toView(new Example(id, name));
    }

    private static ExampleView toView(Example example) {
        return new ExampleView(example.id(), example.name());
    }
}
