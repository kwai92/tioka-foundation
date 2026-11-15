package com.foundation.example.archfixture;

/**
 * 反例夹具：含 {@code tenantId} 字段，用于验证规则集确实拦停违例
 * （ADR-029 第 60 条：单企业独立部署，禁止 tenantId）。
 *
 * <p>位于 test 源码树，被 {@code DoNotIncludeTests} 排除，不会污染示例工程的正式分析集。
 */
public class TenantFieldFixture {

    private String tenantId;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
