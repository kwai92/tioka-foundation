package com.foundation.{app}.example.api;

/**
 * 示例对外契约（BAS-25 §模块分层）。
 *
 * <p>跨模块编译期只允许依赖本 {@code -api}：其他模块（如 {@code -server}）只引本接口，
 * 不直接依赖 {@code {app}-example-biz}。
 */
public interface ExampleApi {

    /** 按 ID 取示例视图（演示读操作与当前操作者）。 */
    ExampleView get(String id);

    /** 新建示例（演示写操作，由控制器加权限与审计注解后调用）。 */
    ExampleView create(String name);
}
