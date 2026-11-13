package com.foundation.authz.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.authz.domain.GrantSource;
import com.foundation.authz.domain.GrantSubjectType;
import com.foundation.authz.domain.RoleGrant;
import com.foundation.authz.infrastructure.mapper.RoleGrantMapper;
import com.foundation.authz.infrastructure.persistence.RoleGrantDO;

import java.util.List;
import java.util.Optional;

/** 授权仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisRoleGrantRepository implements RoleGrantRepository {

    private final RoleGrantMapper mapper;

    public MybatisRoleGrantRepository(RoleGrantMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<RoleGrant> list(GrantSubjectType subjectType, String subjectId) {
        return mapper.selectList(Wrappers.<RoleGrantDO>lambdaQuery()
                        .eq(subjectType != null, RoleGrantDO::getSubjectType,
                                subjectType == null ? null : subjectType.name())
                        .eq(subjectId != null, RoleGrantDO::getSubjectId, subjectId)
                        .orderByAsc(RoleGrantDO::getId))
                .stream().map(MybatisRoleGrantRepository::toDomain).toList();
    }

    @Override
    public Optional<RoleGrant> find(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisRoleGrantRepository::toDomain);
    }

    @Override
    public void insert(RoleGrant grant) {
        RoleGrantDO row = new RoleGrantDO();
        row.setId(grant.id());
        row.setRoleCode(grant.roleCode());
        row.setSubjectType(grant.subjectType().name());
        row.setSubjectId(grant.subjectId());
        row.setIncludeDescendants(grant.includeDescendants());
        row.setEffectiveFrom(grant.effectiveFrom());
        row.setEffectiveTo(grant.effectiveTo());
        row.setSource(grant.source().name());
        mapper.insert(row);
    }

    @Override
    public void delete(String id) {
        // 主数据走逻辑删（48-CS §4）
        mapper.deleteById(id);
    }

    @Override
    public long countByRole(String roleCode) {
        Long count = mapper.selectCount(Wrappers.<RoleGrantDO>lambdaQuery()
                .eq(RoleGrantDO::getRoleCode, roleCode));
        return count == null ? 0L : count;
    }

    private static RoleGrant toDomain(RoleGrantDO row) {
        return new RoleGrant(row.getId(), row.getRoleCode(), GrantSubjectType.of(row.getSubjectType()),
                row.getSubjectId(), Boolean.TRUE.equals(row.getIncludeDescendants()),
                row.getEffectiveFrom(), row.getEffectiveTo(), GrantSource.of(row.getSource()));
    }
}
