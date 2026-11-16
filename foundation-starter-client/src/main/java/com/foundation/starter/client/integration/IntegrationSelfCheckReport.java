package com.foundation.starter.client.integration;

import java.util.List;

/**
 * 派生应用接入自检报告（ADR-051 第 4 条「接入以自检为完成标准」）。
 *
 * <p>平台只定义检查项与结果形状，不解释任何应用的权限码语义。派生应用在测试或运维端点里
 * 调用 {@link FoundationIntegrationSelfCheck#run(String, java.util.function.Predicate)}
 * 生成本报告；{@code WARN} 表示「可选能力未接或探针未执行」，不阻塞接入完成，
 * 只有 {@code FAIL} 才代表接入不成立。
 */
public record IntegrationSelfCheckReport(List<Check> checks) {

    public enum Status {
        PASS,
        WARN,
        FAIL
    }

    public record Check(String name, Status status, String detail) {
    }

    public IntegrationSelfCheckReport {
        checks = List.copyOf(checks);
    }

    /** 没有 FAIL 即视为接入自检通过；WARN 允许存在（如网关注入令牌、P1 事件失效待接）。 */
    public boolean allPassed() {
        return checks.stream().noneMatch(check -> check.status() == Status.FAIL);
    }

    public List<Check> failures() {
        return checks.stream().filter(check -> check.status() == Status.FAIL).toList();
    }

    public long count(Status status) {
        return checks.stream().filter(check -> check.status() == status).count();
    }

    public Check check(String name) {
        return checks.stream().filter(check -> check.name().equals(name)).findFirst().orElse(null);
    }
}
