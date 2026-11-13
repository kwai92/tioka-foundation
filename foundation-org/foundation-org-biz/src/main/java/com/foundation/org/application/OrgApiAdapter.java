package com.foundation.org.application;

import com.foundation.org.api.OrgApi;
import com.foundation.org.api.dto.OrgUnitView;
import com.foundation.org.domain.OrgUnit;
import com.foundation.org.infrastructure.OrgUnitRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@link OrgApi} 只读契约实现（F-03 §5）：供 BAS-05 权限、BAS-06 数据范围等模块依赖。
 *
 * <p>写操作不经本接口，一律走管理台 REST 与应用服务。
 */
public class OrgApiAdapter implements OrgApi {

    private final OrgUnitRepository orgUnits;

    public OrgApiAdapter(OrgUnitRepository orgUnits) {
        this.orgUnits = orgUnits;
    }

    @Override
    public Optional<OrgUnitView> findById(String orgUnitId) {
        return orgUnits.findById(orgUnitId).map(OrgUnit::toView);
    }

    @Override
    public Optional<OrgUnitView> findByCode(String code) {
        return orgUnits.findByCode(code).map(OrgUnit::toView);
    }

    @Override
    public List<OrgUnitView> childrenOf(String orgUnitId) {
        return orgUnits.childrenOf(orgUnitId).stream().map(OrgUnit::toView).toList();
    }

    @Override
    public Set<String> descendantIds(String orgUnitId) {
        return orgUnitId == null ? Set.of() : orgUnits.descendantIds(orgUnitId);
    }
}
