package com.foundation.starter.client.local;

import com.foundation.starter.client.org.OrgClient;
import com.foundation.starter.client.org.OrgUnit;

import java.util.List;
import java.util.Optional;

/** 组织查询的本地种子实现（ADP-08）：按 parentId 建树，不发起任何 HTTP 调用。 */
public class LocalOrgClient implements OrgClient {

    private final LocalFoundationData data;

    public LocalOrgClient(LocalFoundationData data) {
        this.data = data;
    }

    @Override
    public Optional<OrgUnit> byCode(String orgCode) {
        return data.orgByCode(orgCode);
    }

    @Override
    public List<OrgUnit> children(String orgCode) {
        return data.children(orgCode);
    }
}
