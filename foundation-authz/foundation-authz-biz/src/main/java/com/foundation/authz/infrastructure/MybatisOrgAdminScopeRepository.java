package com.foundation.authz.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.authz.domain.OrgAdminScope;
import com.foundation.authz.infrastructure.mapper.OrgAdminScopeMapper;
import com.foundation.authz.infrastructure.persistence.OrgAdminScopeDO;

import java.util.List;
import java.util.Optional;

/** 组织分级管理员范围仓储的 MyBatis-Plus 实现（48-CS §4）；白名单以 JSON 数组编解码。 */
public class MybatisOrgAdminScopeRepository implements OrgAdminScopeRepository {

    private final OrgAdminScopeMapper mapper;
    private final ObjectMapper json;

    public MybatisOrgAdminScopeRepository(OrgAdminScopeMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    public List<OrgAdminScope> list(String userId) {
        return mapper.selectList(Wrappers.<OrgAdminScopeDO>lambdaQuery()
                        .eq(userId != null, OrgAdminScopeDO::getUserId, userId)
                        .orderByAsc(OrgAdminScopeDO::getUserId)
                        .orderByAsc(OrgAdminScopeDO::getOrgUnitId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<OrgAdminScope> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<OrgAdminScope> findByUserAndOrgUnit(String userId, String orgUnitId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<OrgAdminScopeDO>lambdaQuery()
                .eq(OrgAdminScopeDO::getUserId, userId)
                .eq(OrgAdminScopeDO::getOrgUnitId, orgUnitId))).map(this::toDomain);
    }

    @Override
    public void insert(OrgAdminScope scope) {
        OrgAdminScopeDO row = new OrgAdminScopeDO();
        row.setId(scope.id());
        row.setUserId(scope.userId());
        row.setOrgUnitId(scope.orgUnitId());
        row.setIncludeDescendants(scope.includeDescendants());
        row.setGrantableRoles(writeRoles(scope.grantableRoles()));
        mapper.insert(row);
    }

    @Override
    public void update(OrgAdminScope scope) {
        OrgAdminScopeDO row = new OrgAdminScopeDO();
        row.setId(scope.id());
        row.setIncludeDescendants(scope.includeDescendants());
        row.setGrantableRoles(writeRoles(scope.grantableRoles()));
        mapper.updateById(row);
    }

    private String writeRoles(List<String> roles) {
        try {
            return json.writeValueAsString(roles == null ? List.of() : roles);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("可授予角色白名单序列化失败", ex);
        }
    }

    private List<String> readRoles(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(text, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("可授予角色白名单反序列化失败", ex);
        }
    }

    private OrgAdminScope toDomain(OrgAdminScopeDO row) {
        return new OrgAdminScope(row.getId(), row.getUserId(), row.getOrgUnitId(),
                row.getIncludeDescendants() == null || row.getIncludeDescendants(), readRoles(row.getGrantableRoles()));
    }
}
