package com.foundation.test.fixture.tenant;

/**
 * 规则 3 子规则 A 反例：含 tenantId 字段。
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
