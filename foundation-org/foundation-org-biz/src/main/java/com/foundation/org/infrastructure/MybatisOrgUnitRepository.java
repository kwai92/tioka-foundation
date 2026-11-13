package com.foundation.org.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foundation.org.api.dto.OrgStatus;
import com.foundation.org.api.dto.OrgUnitType;
import com.foundation.org.domain.OrgUnit;
import com.foundation.org.infrastructure.mapper.OrgUnitMapper;
import com.foundation.org.infrastructure.persistence.OrgUnitDO;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 组织单元仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisOrgUnitRepository implements OrgUnitRepository {

    private final OrgUnitMapper mapper;

    public MybatisOrgUnitRepository(OrgUnitMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<OrgUnit> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisOrgUnitRepository::toDomain);
    }

    @Override
    public Optional<OrgUnit> findByCode(String code) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<OrgUnitDO>lambdaQuery().eq(OrgUnitDO::getCode, code))).map(MybatisOrgUnitRepository::toDomain);
    }

    @Override
    public Optional<OrgUnit> findBySourceExternalId(String source, String externalId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<OrgUnitDO>lambdaQuery()
                .eq(OrgUnitDO::getSource, source)
                .eq(OrgUnitDO::getExternalId, externalId))).map(MybatisOrgUnitRepository::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return mapper.selectCount(Wrappers.<OrgUnitDO>lambdaQuery().eq(OrgUnitDO::getCode, code)) > 0;
    }

    @Override
    public List<OrgUnit> childrenOf(String parentId) {
        LambdaQueryWrapper<OrgUnitDO> wrapper = Wrappers.<OrgUnitDO>lambdaQuery();
        if (parentId == null || parentId.isBlank()) {
            wrapper.isNull(OrgUnitDO::getParentId);
        } else {
            wrapper.eq(OrgUnitDO::getParentId, parentId);
        }
        wrapper.orderByAsc(OrgUnitDO::getSortNo).orderByAsc(OrgUnitDO::getCode);
        return mapper.selectList(wrapper).stream().map(MybatisOrgUnitRepository::toDomain).toList();
    }

    @Override
    public List<OrgUnit> subtreeOf(String orgUnitId) {
        OrgUnitDO self = mapper.selectById(orgUnitId);
        if (self == null) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<OrgUnitDO>lambdaQuery()
                        .and(w -> w.eq(OrgUnitDO::getId, self.getId())
                                .or().likeRight(OrgUnitDO::getPath, self.getPath() + "/")))
                .stream().map(MybatisOrgUnitRepository::toDomain).toList();
    }

    @Override
    public List<OrgUnit> findAll() {
        return mapper.selectList(Wrappers.<OrgUnitDO>lambdaQuery()
                        .orderByAsc(OrgUnitDO::getPath).orderByAsc(OrgUnitDO::getSortNo))
                .stream().map(MybatisOrgUnitRepository::toDomain).toList();
    }

    @Override
    public Set<String> descendantIds(String orgUnitId) {
        OrgUnitDO self = mapper.selectById(orgUnitId);
        if (self == null) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        ids.add(self.getId());
        mapper.selectList(Wrappers.<OrgUnitDO>lambdaQuery()
                        .select(OrgUnitDO::getId)
                        .likeRight(OrgUnitDO::getPath, self.getPath() + "/"))
                .forEach(row -> ids.add(row.getId()));
        return ids;
    }

    @Override
    public List<OrgUnit> search(String q, OrgUnitType type, OrgStatus status, int page, int size) {
        Page<OrgUnitDO> result = mapper.selectPage(new Page<>(page, size), searchWrapper(q, type, status));
        return result.getRecords().stream().map(MybatisOrgUnitRepository::toDomain).toList();
    }

    @Override
    public long count(String q, OrgUnitType type, OrgStatus status) {
        return mapper.selectCount(searchWrapper(q, type, status));
    }

    @Override
    public void insert(OrgUnit orgUnit) {
        mapper.insert(toDO(orgUnit));
    }

    @Override
    public void update(OrgUnit orgUnit) {
        mapper.update(null, Wrappers.<OrgUnitDO>lambdaUpdate()
                .set(OrgUnitDO::getCode, orgUnit.code())
                .set(OrgUnitDO::getName, orgUnit.name())
                .set(OrgUnitDO::getType, orgUnit.type().name())
                .set(OrgUnitDO::getParentId, orgUnit.parentId())
                .set(OrgUnitDO::getPath, orgUnit.path())
                .set(OrgUnitDO::getDepth, orgUnit.depth())
                .set(OrgUnitDO::getSortNo, orgUnit.sortNo())
                .set(OrgUnitDO::getLeaderPersonId, orgUnit.leaderPersonId())
                .set(OrgUnitDO::getEffectiveAt, toLocalDateTime(orgUnit.effectiveAt()))
                .set(OrgUnitDO::getStatus, orgUnit.status().name())
                .set(OrgUnitDO::getSource, orgUnit.source())
                .set(OrgUnitDO::getExternalId, orgUnit.externalId())
                .set(OrgUnitDO::getVersion, orgUnit.version())
                .eq(OrgUnitDO::getId, orgUnit.id()));
    }

    @Override
    public int rewriteSubtree(String oldPrefix, String newPrefix, int depthDelta) {
        UpdateWrapper<OrgUnitDO> wrapper = new UpdateWrapper<>();
        wrapper.setSql("path = replace(path, {0}, {1})", oldPrefix, newPrefix);
        wrapper.setSql("depth = depth + {0}", depthDelta);
        wrapper.likeRight("path", oldPrefix);
        return mapper.update(null, wrapper);
    }

    private LambdaQueryWrapper<OrgUnitDO> searchWrapper(String q, OrgUnitType type, OrgStatus status) {
        LambdaQueryWrapper<OrgUnitDO> wrapper = Wrappers.<OrgUnitDO>lambdaQuery();
        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.trim() + "%";
            wrapper.and(w -> w.apply("lower(name) LIKE lower({0})", pattern)
                    .or().apply("lower(code) LIKE lower({0})", pattern));
        }
        if (type != null) {
            wrapper.eq(OrgUnitDO::getType, type.name());
        }
        if (status != null) {
            wrapper.eq(OrgUnitDO::getStatus, status.name());
        }
        wrapper.orderByAsc(OrgUnitDO::getPath).orderByAsc(OrgUnitDO::getSortNo);
        return wrapper;
    }

    private static OrgUnitDO toDO(OrgUnit orgUnit) {
        OrgUnitDO row = new OrgUnitDO();
        row.setId(orgUnit.id());
        row.setCode(orgUnit.code());
        row.setName(orgUnit.name());
        row.setType(orgUnit.type().name());
        row.setParentId(orgUnit.parentId());
        row.setPath(orgUnit.path());
        row.setDepth(orgUnit.depth());
        row.setSortNo(orgUnit.sortNo());
        row.setLeaderPersonId(orgUnit.leaderPersonId());
        row.setEffectiveAt(toLocalDateTime(orgUnit.effectiveAt()));
        row.setStatus(orgUnit.status().name());
        row.setSource(orgUnit.source());
        row.setExternalId(orgUnit.externalId());
        row.setVersion(orgUnit.version());
        return row;
    }

    private static OrgUnit toDomain(OrgUnitDO row) {
        return new OrgUnit(row.getId(), row.getCode(), row.getName(), OrgUnitType.valueOf(row.getType()),
                row.getParentId(), row.getPath(), row.getDepth() == null ? 1 : row.getDepth(),
                row.getSortNo() == null ? 0 : row.getSortNo(), row.getLeaderPersonId(),
                toInstant(row.getEffectiveAt()), OrgStatus.valueOf(row.getStatus()), row.getSource(),
                row.getExternalId(), row.getVersion() == null ? 0 : row.getVersion());
    }

    private static LocalDateTime toLocalDateTime(java.time.Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static java.time.Instant toInstant(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }
}
