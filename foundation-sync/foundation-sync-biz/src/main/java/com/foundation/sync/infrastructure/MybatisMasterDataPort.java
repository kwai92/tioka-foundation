package com.foundation.sync.infrastructure;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import com.foundation.sync.domain.LandedRecord;
import com.foundation.sync.domain.LocalLockResolver;
import com.foundation.sync.domain.MasterDataPort;
import com.foundation.sync.infrastructure.mapper.SyncExternalIdentityMapper;
import com.foundation.sync.infrastructure.mapper.SyncOrgUnitMapper;
import com.foundation.sync.infrastructure.mapper.SyncPersonMapper;
import com.foundation.sync.infrastructure.mapper.SyncPositionMapper;
import com.foundation.sync.infrastructure.persistence.SyncExternalIdentityDO;
import com.foundation.sync.infrastructure.persistence.SyncOrgUnitDO;
import com.foundation.sync.infrastructure.persistence.SyncPersonDO;
import com.foundation.sync.infrastructure.persistence.SyncPositionDO;
import com.foundation.sync.spi.SyncKey;
import com.foundation.sync.spi.SyncObjectType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 主数据落地端口的 MyBatis-Plus 实现（F-08 BAS-11 §7、§12）。
 *
 * <p>同步引擎直连 {@code fdn_person} / {@code fdn_org_unit} / {@code fdn_position} /
 * {@code fdn_external_identity} 做幂等落地：不改 BAS-02 / BAS-03 的 {@code -biz}，跨模块只依赖
 * {@code -api}（F-05 硬约束）。
 *
 * <p>内置字段名（{@code fdn_sync_field_mapping.internal_field} 的取值口径）：
 * <ul>
 *   <li>PERSON：{@code empNo}、{@code name}、{@code type}、{@code status}、{@code mobileCipher}、
 *       {@code emailCipher}、{@code mobileHash}、{@code emailHash}</li>
 *   <li>ORG_UNIT：{@code code}、{@code name}、{@code type}、{@code sortNo}、{@code leaderPersonId}、
 *       {@code effectiveAt}、{@code status}（上级由 {@code parentExternalId} 单独承载）</li>
 *   <li>POSITION：{@code code}、{@code name}、{@code orgUnitId}、{@code status}、{@code sortNo}</li>
 *   <li>EXTERNAL_IDENTITY：{@code personId}、{@code linkMode}、{@code linkedAt}</li>
 * </ul>
 * 出现未登记的内置字段名即拒绝，让配置错误在落地时立刻暴露，而不是静默丢弃。
 *
 * <p>组织单元建树沿用 BAS-02 口径：{@code path} 物化路径 {@code /org_x/org_y}、{@code depth} 根为 1
 * 且受 {@code ck_org_unit_depth}（1〜10）约束；停用前置校验（存在下级不得停用）与 BAS-02 一致。
 */
public class MybatisMasterDataPort implements MasterDataPort {

    /** 同步引擎写主数据时的审计操作人标记（来源溯源另有 {@code source} 列）。 */
    private static final String AUDIT_OPERATOR = "sync";

    /** 组织树深度上限，与 {@code ck_org_unit_depth} 一致。 */
    private static final int MAX_ORG_DEPTH = 10;

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";

    private static final String PERSON_PREFIX = "per";
    private static final String ORG_UNIT_PREFIX = "org";
    private static final String POSITION_PREFIX = "pos";
    private static final String EXTERNAL_IDENTITY_PREFIX = "xid";

    /** {@code ck_external_identity_source} 允许的外部身份来源；越界在落地前拒绝。 */
    private static final Set<String> IDENTITY_SOURCES = Set.of("FEISHU_OPEN_ID", "FEISHU_UNION_ID",
            "DINGTALK_USER_ID", "WECOM_USER_ID", "AD_OBJECTGUID", "SCIM_EXTERNAL_ID");

    /** {@code ck_external_identity_link_mode} 允许的关联方式。 */
    private static final Set<String> LINK_MODES = Set.of("AUTO", "SELF_CONFIRMED", "ADMIN");

    private final SyncPersonMapper personMapper;
    private final SyncOrgUnitMapper orgUnitMapper;
    private final SyncPositionMapper positionMapper;
    private final SyncExternalIdentityMapper externalIdentityMapper;
    private final LocalLockResolver lockResolver = new LocalLockResolver();

    public MybatisMasterDataPort(SyncPersonMapper personMapper,
                                 SyncOrgUnitMapper orgUnitMapper,
                                 SyncPositionMapper positionMapper,
                                 SyncExternalIdentityMapper externalIdentityMapper) {
        this.personMapper = personMapper;
        this.orgUnitMapper = orgUnitMapper;
        this.positionMapper = positionMapper;
        this.externalIdentityMapper = externalIdentityMapper;
    }

    // ------------------------------------------------------------------ 读

    @Override
    public Optional<LandedRecord> find(SyncObjectType objectType, SyncKey key) {
        return switch (objectType) {
            case PERSON -> Optional.ofNullable(personMapper.selectOne(Wrappers.<SyncPersonDO>lambdaQuery()
                            .eq(SyncPersonDO::getSource, key.sourceType())
                            .eq(SyncPersonDO::getExternalId, key.externalId())))
                    .map(MybatisMasterDataPort::toPersonRecord);
            case ORG_UNIT -> {
                SyncOrgUnitDO row = orgUnitMapper.selectOne(Wrappers.<SyncOrgUnitDO>lambdaQuery()
                        .eq(SyncOrgUnitDO::getSource, key.sourceType())
                        .eq(SyncOrgUnitDO::getExternalId, key.externalId()));
                yield row == null ? Optional.empty()
                        : Optional.of(toOrgUnitRecord(row, resolveParentExternalId(row.getParentId())));
            }
            case POSITION -> Optional.ofNullable(positionMapper.selectOne(Wrappers.<SyncPositionDO>lambdaQuery()
                            .eq(SyncPositionDO::getSource, key.sourceType())
                            .eq(SyncPositionDO::getExternalId, key.externalId())))
                    .map(MybatisMasterDataPort::toPositionRecord);
            case EXTERNAL_IDENTITY -> Optional.ofNullable(externalIdentityMapper.selectOne(
                            Wrappers.<SyncExternalIdentityDO>lambdaQuery()
                                    .eq(SyncExternalIdentityDO::getSource, key.sourceType())
                                    .eq(SyncExternalIdentityDO::getExternalId, key.externalId())))
                    .map(MybatisMasterDataPort::toExternalIdentityRecord);
            default -> throw unsupportedObject(objectType);
        };
    }

    @Override
    public List<LandedRecord> listBySource(SyncObjectType objectType, String sourceType) {
        return switch (objectType) {
            case PERSON -> personMapper.selectList(Wrappers.<SyncPersonDO>lambdaQuery()
                            .eq(SyncPersonDO::getSource, sourceType)).stream()
                    .filter(MybatisMasterDataPort::identifiable)
                    .map(MybatisMasterDataPort::toPersonRecord).toList();
            case ORG_UNIT -> listOrgUnitsBySource(sourceType);
            case POSITION -> positionMapper.selectList(Wrappers.<SyncPositionDO>lambdaQuery()
                            .eq(SyncPositionDO::getSource, sourceType)).stream()
                    .filter(row -> identifiable(row.getSource(), row.getExternalId()))
                    .map(MybatisMasterDataPort::toPositionRecord).toList();
            case EXTERNAL_IDENTITY -> externalIdentityMapper.selectList(
                            Wrappers.<SyncExternalIdentityDO>lambdaQuery()
                                    .eq(SyncExternalIdentityDO::getSource, sourceType)).stream()
                    .filter(row -> identifiable(row.getSource(), row.getExternalId()))
                    .map(MybatisMasterDataPort::toExternalIdentityRecord).toList();
            default -> throw unsupportedObject(objectType);
        };
    }

    private List<LandedRecord> listOrgUnitsBySource(String sourceType) {
        List<SyncOrgUnitDO> rows = orgUnitMapper.selectList(Wrappers.<SyncOrgUnitDO>lambdaQuery()
                .eq(SyncOrgUnitDO::getSource, sourceType)).stream()
                .filter(row -> identifiable(row.getSource(), row.getExternalId())).toList();
        Map<String, String> externalById = parentExternalIds(rows);
        return rows.stream()
                .map(row -> toOrgUnitRecord(row, externalById.get(row.getParentId())))
                .toList();
    }

    /** 批量解析上级的外部 ID（子树判定按外部 ID 对齐，见 {@link LandedRecord#parentExternalId()}）。 */
    private Map<String, String> parentExternalIds(List<SyncOrgUnitDO> rows) {
        Set<String> parentIds = new LinkedHashSet<>();
        for (SyncOrgUnitDO row : rows) {
            if (row.getParentId() != null) {
                parentIds.add(row.getParentId());
            }
        }
        if (parentIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> externalById = new LinkedHashMap<>();
        for (SyncOrgUnitDO parent : orgUnitMapper.selectBatchIds(parentIds)) {
            externalById.put(parent.getId(),
                    parent.getExternalId() == null ? parent.getId() : parent.getExternalId());
        }
        return externalById;
    }

    // ------------------------------------------------------------------ 写

    @Override
    public LandedRecord create(SyncObjectType objectType, SyncKey key, Map<String, Object> fields, SyncKey parentKey) {
        Map<String, Object> safe = fields == null ? Map.of() : fields;
        return switch (objectType) {
            case PERSON -> createPerson(key, safe);
            case ORG_UNIT -> createOrgUnit(key, safe, parentKey);
            case POSITION -> createPosition(key, safe);
            case EXTERNAL_IDENTITY -> createExternalIdentity(key, safe);
            default -> throw unsupportedObject(objectType);
        };
    }

    @Override
    public LandedRecord update(SyncObjectType objectType, LandedRecord existing, Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return existing;
        }
        return switch (objectType) {
            case PERSON -> updatePerson(existing, fields);
            case ORG_UNIT -> updateOrgUnit(existing, fields);
            case POSITION -> updatePosition(existing, fields);
            case EXTERNAL_IDENTITY -> updateExternalIdentity(existing, fields);
            default -> throw unsupportedObject(objectType);
        };
    }

    @Override
    public LandedRecord disable(SyncObjectType objectType, LandedRecord existing) {
        return switch (objectType) {
            case PERSON -> disablePerson(existing);
            case ORG_UNIT -> disableOrgUnit(existing);
            case POSITION -> disablePosition(existing);
            case EXTERNAL_IDENTITY -> disableExternalIdentity(existing);
            default -> throw unsupportedObject(objectType);
        };
    }

    @Override
    public LandedRecord move(SyncObjectType objectType, LandedRecord existing, SyncKey newParentKey) {
        if (objectType != SyncObjectType.ORG_UNIT) {
            throw new BizException(ErrorCode.BAD_REQUEST, "仅组织单元支持移动：" + objectType);
        }
        return moveOrgUnit(existing, newParentKey);
    }

    @Override
    public Set<String> lockedFields(SyncObjectType objectType, String localId) {
        return switch (objectType) {
            case PERSON -> lockResolver.parse(requirePerson(localId).getLocalLockedFields());
            case ORG_UNIT -> lockResolver.parse(requireOrgUnit(localId).getLocalLockedFields());
            default -> Set.of();
        };
    }

    @Override
    public void setLockedFields(SyncObjectType objectType, String localId, Set<String> fields) {
        String csv = lockResolver.serialize(fields);
        switch (objectType) {
            case PERSON -> personMapper.update(null, Wrappers.<SyncPersonDO>lambdaUpdate()
                    .set(SyncPersonDO::getLocalLockedFields, csv)
                    .set(SyncPersonDO::getUpdateAt, now())
                    .set(SyncPersonDO::getUpdateBy, AUDIT_OPERATOR)
                    .eq(SyncPersonDO::getId, requirePerson(localId).getId()));
            case ORG_UNIT -> orgUnitMapper.update(null, Wrappers.<SyncOrgUnitDO>lambdaUpdate()
                    .set(SyncOrgUnitDO::getLocalLockedFields, csv)
                    .set(SyncOrgUnitDO::getUpdateAt, now())
                    .set(SyncOrgUnitDO::getUpdateBy, AUDIT_OPERATOR)
                    .eq(SyncOrgUnitDO::getId, requireOrgUnit(localId).getId()));
            default -> throw new BizException(ErrorCode.BAD_REQUEST, "该对象不支持档案字段锁定：" + objectType);
        }
    }

    // ------------------------------------------------------------------ 人员

    private LandedRecord createPerson(SyncKey key, Map<String, Object> fields) {
        SyncPersonDO row = new SyncPersonDO();
        row.setId(Ids.newId(PERSON_PREFIX));
        row.setSource(key.sourceType());
        row.setExternalId(key.externalId());
        applyPerson(row, fields);
        if (row.getStatus() == null) {
            row.setStatus(STATUS_ACTIVE);
        }
        row.setVersion(0L);
        row.setLocalLockedFields("");
        markAudit(row);
        personMapper.insert(row);
        return toPersonRecord(row);
    }

    private LandedRecord updatePerson(LandedRecord existing, Map<String, Object> fields) {
        SyncPersonDO row = requirePerson(existing.localId());
        long version = versionOf(row.getVersion()) + 1;
        LambdaUpdateWrapper<SyncPersonDO> update = Wrappers.<SyncPersonDO>lambdaUpdate();
        boolean settable = false;
        for (Map.Entry<String, Object> field : fields.entrySet()) {
            String value = text(field.getValue());
            switch (field.getKey()) {
                case "empNo" -> update.set(SyncPersonDO::getEmpNo, value);
                case "name" -> update.set(SyncPersonDO::getName, value);
                case "type" -> update.set(SyncPersonDO::getType, value);
                case "status" -> update.set(SyncPersonDO::getStatus, upper(value));
                case "mobileCipher" -> update.set(SyncPersonDO::getMobileCipher, value);
                case "emailCipher" -> update.set(SyncPersonDO::getEmailCipher, value);
                case "mobileHash" -> update.set(SyncPersonDO::getMobileHash, value);
                case "emailHash" -> update.set(SyncPersonDO::getEmailHash, value);
                default -> throw unsupportedField(SyncObjectType.PERSON, field.getKey());
            }
            settable = true;
        }
        if (!settable) {
            return existing;
        }
        update.set(SyncPersonDO::getVersion, version)
                .set(SyncPersonDO::getUpdateAt, now())
                .set(SyncPersonDO::getUpdateBy, AUDIT_OPERATOR)
                .eq(SyncPersonDO::getId, row.getId());
        personMapper.update(null, update);
        applyPerson(row, fields);
        row.setVersion(version);
        return toPersonRecord(row);
    }

    private LandedRecord disablePerson(LandedRecord existing) {
        SyncPersonDO row = requirePerson(existing.localId());
        long version = versionOf(row.getVersion()) + 1;
        personMapper.update(null, Wrappers.<SyncPersonDO>lambdaUpdate()
                .set(SyncPersonDO::getStatus, STATUS_DISABLED)
                .set(SyncPersonDO::getVersion, version)
                .set(SyncPersonDO::getUpdateAt, now())
                .set(SyncPersonDO::getUpdateBy, AUDIT_OPERATOR)
                .eq(SyncPersonDO::getId, row.getId()));
        row.setStatus(STATUS_DISABLED);
        row.setVersion(version);
        return toPersonRecord(row);
    }

    private static void applyPerson(SyncPersonDO row, Map<String, Object> fields) {
        for (Map.Entry<String, Object> field : fields.entrySet()) {
            String value = text(field.getValue());
            switch (field.getKey()) {
                case "empNo" -> row.setEmpNo(value);
                case "name" -> row.setName(value);
                case "type" -> row.setType(value);
                case "status" -> row.setStatus(upper(value));
                case "mobileCipher" -> row.setMobileCipher(value);
                case "emailCipher" -> row.setEmailCipher(value);
                case "mobileHash" -> row.setMobileHash(value);
                case "emailHash" -> row.setEmailHash(value);
                default -> throw unsupportedField(SyncObjectType.PERSON, field.getKey());
            }
        }
    }

    // ------------------------------------------------------------------ 组织单元

    private LandedRecord createOrgUnit(SyncKey key, Map<String, Object> fields, SyncKey parentKey) {
        SyncOrgUnitDO row = new SyncOrgUnitDO();
        row.setId(Ids.newId(ORG_UNIT_PREFIX));
        row.setSource(key.sourceType());
        row.setExternalId(key.externalId());
        applyOrgUnit(row, fields);
        if (row.getStatus() == null) {
            row.setStatus(STATUS_ACTIVE);
        }
        if (row.getSortNo() == null) {
            row.setSortNo(0);
        }
        row.setVersion(0L);
        row.setLocalLockedFields("");
        SyncOrgUnitDO parent = parentKey == null ? null : requireOrgUnitByKey(parentKey);
        row.setParentId(parent == null ? null : parent.getId());
        row.setPath(parent == null ? pathOf(row.getId()) : parent.getPath() + "/" + row.getId());
        row.setDepth(parent == null ? 1 : depthOf(parent) + 1);
        assertDepthWithinLimit(row.getDepth());
        markAudit(row);
        orgUnitMapper.insert(row);
        return toOrgUnitRecord(row, parentKey == null ? null : parentKey.externalId());
    }

    private LandedRecord updateOrgUnit(LandedRecord existing, Map<String, Object> fields) {
        SyncOrgUnitDO row = requireOrgUnit(existing.localId());
        long version = versionOf(row.getVersion()) + 1;
        LambdaUpdateWrapper<SyncOrgUnitDO> update = Wrappers.<SyncOrgUnitDO>lambdaUpdate();
        boolean settable = false;
        for (Map.Entry<String, Object> field : fields.entrySet()) {
            switch (field.getKey()) {
                case "code" -> update.set(SyncOrgUnitDO::getCode, text(field.getValue()));
                case "name" -> update.set(SyncOrgUnitDO::getName, text(field.getValue()));
                case "type" -> update.set(SyncOrgUnitDO::getType, text(field.getValue()));
                case "sortNo" -> update.set(SyncOrgUnitDO::getSortNo, asInt("sortNo", field.getValue()));
                case "leaderPersonId" -> update.set(SyncOrgUnitDO::getLeaderPersonId, text(field.getValue()));
                case "effectiveAt" ->
                        update.set(SyncOrgUnitDO::getEffectiveAt, asDateTime("effectiveAt", field.getValue()));
                case "status" -> update.set(SyncOrgUnitDO::getStatus, upper(text(field.getValue())));
                default -> throw unsupportedField(SyncObjectType.ORG_UNIT, field.getKey());
            }
            settable = true;
        }
        if (!settable) {
            return existing;
        }
        update.set(SyncOrgUnitDO::getVersion, version)
                .set(SyncOrgUnitDO::getUpdateAt, now())
                .set(SyncOrgUnitDO::getUpdateBy, AUDIT_OPERATOR)
                .eq(SyncOrgUnitDO::getId, row.getId());
        orgUnitMapper.update(null, update);
        applyOrgUnit(row, fields);
        row.setVersion(version);
        return toOrgUnitRecord(row, resolveParentExternalId(row.getParentId()));
    }

    private LandedRecord disableOrgUnit(LandedRecord existing) {
        SyncOrgUnitDO row = requireOrgUnit(existing.localId());
        assertDeactivatable(row.getId());
        long version = versionOf(row.getVersion()) + 1;
        orgUnitMapper.update(null, Wrappers.<SyncOrgUnitDO>lambdaUpdate()
                .set(SyncOrgUnitDO::getStatus, STATUS_DISABLED)
                .set(SyncOrgUnitDO::getVersion, version)
                .set(SyncOrgUnitDO::getUpdateAt, now())
                .set(SyncOrgUnitDO::getUpdateBy, AUDIT_OPERATOR)
                .eq(SyncOrgUnitDO::getId, row.getId()));
        row.setStatus(STATUS_DISABLED);
        row.setVersion(version);
        return toOrgUnitRecord(row, resolveParentExternalId(row.getParentId()));
    }

    private LandedRecord moveOrgUnit(LandedRecord existing, SyncKey newParentKey) {
        SyncOrgUnitDO row = requireOrgUnit(existing.localId());
        SyncOrgUnitDO parent = newParentKey == null ? null : requireOrgUnitByKey(newParentKey);
        if (parent != null && parent.getId().equals(row.getId())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "组织单元不能以自身为上级：" + row.getId());
        }
        String oldPath = row.getPath();
        int oldDepth = depthOf(row);
        if (parent != null && isWithinSubtree(parent.getPath(), oldPath)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "移动会形成环：目标上级在当前组织单元的子树内");
        }
        String newPath = parent == null ? pathOf(row.getId()) : parent.getPath() + "/" + row.getId();
        int newDepth = parent == null ? 1 : depthOf(parent) + 1;
        assertSubtreeDepthWithinLimit(oldPath, oldDepth, newDepth);
        long version = versionOf(row.getVersion()) + 1;
        orgUnitMapper.update(null, Wrappers.<SyncOrgUnitDO>lambdaUpdate()
                .set(SyncOrgUnitDO::getParentId, parent == null ? null : parent.getId())
                .set(SyncOrgUnitDO::getPath, newPath)
                .set(SyncOrgUnitDO::getDepth, newDepth)
                .set(SyncOrgUnitDO::getVersion, version)
                .set(SyncOrgUnitDO::getUpdateAt, now())
                .set(SyncOrgUnitDO::getUpdateBy, AUDIT_OPERATOR)
                .eq(SyncOrgUnitDO::getId, row.getId()));
        if (!newPath.equals(oldPath)) {
            rewriteSubtree(oldPath, newPath, newDepth - oldDepth);
        }
        row.setParentId(parent == null ? null : parent.getId());
        row.setPath(newPath);
        row.setDepth(newDepth);
        row.setVersion(version);
        return toOrgUnitRecord(row, newParentKey == null ? null : newParentKey.externalId());
    }

    /** 子树物化路径与深度级联重写（口径同 BAS-02 {@code OrgUnitRepository#rewriteSubtree}）。 */
    private void rewriteSubtree(String oldPath, String newPath, int depthDelta) {
        orgUnitMapper.update(null, new UpdateWrapper<SyncOrgUnitDO>()
                .setSql("path = replace(path, {0}, {1})", oldPath + "/", newPath + "/")
                .setSql("depth = depth + {0}", depthDelta)
                .setSql("update_at = now()")
                .setSql("update_by = {0}", AUDIT_OPERATOR)
                .likeRight("path", oldPath + "/"));
    }

    private static void applyOrgUnit(SyncOrgUnitDO row, Map<String, Object> fields) {
        for (Map.Entry<String, Object> field : fields.entrySet()) {
            switch (field.getKey()) {
                case "code" -> row.setCode(text(field.getValue()));
                case "name" -> row.setName(text(field.getValue()));
                case "type" -> row.setType(text(field.getValue()));
                case "sortNo" -> row.setSortNo(asInt("sortNo", field.getValue()));
                case "leaderPersonId" -> row.setLeaderPersonId(text(field.getValue()));
                case "effectiveAt" -> row.setEffectiveAt(asDateTime("effectiveAt", field.getValue()));
                case "status" -> row.setStatus(upper(text(field.getValue())));
                default -> throw unsupportedField(SyncObjectType.ORG_UNIT, field.getKey());
            }
        }
    }

    /** 停用前置校验（与 BAS-02 一致：存在下级不得停用）。 */
    private void assertDeactivatable(String localId) {
        long children = orgUnitMapper.selectCount(Wrappers.<SyncOrgUnitDO>lambdaQuery()
                .eq(SyncOrgUnitDO::getParentId, localId));
        if (children > 0) {
            throw new BizException(ErrorCode.CONFLICT, "组织单元存在下级，不能停用：" + localId);
        }
    }

    /** 移动后子树是否仍在深度上限内（{@code ck_org_unit_depth}）。 */
    private void assertSubtreeDepthWithinLimit(String oldPath, int oldDepth, int newDepth) {
        int maxSubtreeDepth = maxDepthOfSubtree(oldPath);
        int newMaxDepth = newDepth + (maxSubtreeDepth - oldDepth);
        assertDepthWithinLimit(newMaxDepth);
    }

    private int maxDepthOfSubtree(String path) {
        int max = 0;
        for (SyncOrgUnitDO row : orgUnitMapper.selectList(Wrappers.<SyncOrgUnitDO>lambdaQuery()
                .select(SyncOrgUnitDO::getDepth, SyncOrgUnitDO::getPath)
                .and(w -> w.eq(SyncOrgUnitDO::getPath, path)
                        .or().likeRight(SyncOrgUnitDO::getPath, path + "/")))) {
            max = Math.max(max, depthOf(row));
        }
        return max;
    }

    private static void assertDepthWithinLimit(int depth) {
        if (depth < 1 || depth > MAX_ORG_DEPTH) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "组织树深度超限：depth=" + depth + "，上限 " + MAX_ORG_DEPTH);
        }
    }

    private static boolean isWithinSubtree(String candidatePath, String subtreeRootPath) {
        if (candidatePath == null || subtreeRootPath == null) {
            return false;
        }
        return candidatePath.equals(subtreeRootPath) || candidatePath.startsWith(subtreeRootPath + "/");
    }

    // ------------------------------------------------------------------ 岗位

    private LandedRecord createPosition(SyncKey key, Map<String, Object> fields) {
        SyncPositionDO row = new SyncPositionDO();
        row.setId(Ids.newId(POSITION_PREFIX));
        row.setSource(key.sourceType());
        row.setExternalId(key.externalId());
        applyPosition(row, fields);
        if (row.getStatus() == null) {
            row.setStatus(STATUS_ACTIVE);
        }
        if (row.getSortNo() == null) {
            row.setSortNo(0);
        }
        markAudit(row);
        positionMapper.insert(row);
        return toPositionRecord(row);
    }

    private LandedRecord updatePosition(LandedRecord existing, Map<String, Object> fields) {
        SyncPositionDO row = requirePosition(existing.localId());
        LambdaUpdateWrapper<SyncPositionDO> update = Wrappers.<SyncPositionDO>lambdaUpdate();
        boolean settable = false;
        for (Map.Entry<String, Object> field : fields.entrySet()) {
            switch (field.getKey()) {
                case "code" -> update.set(SyncPositionDO::getCode, text(field.getValue()));
                case "name" -> update.set(SyncPositionDO::getName, text(field.getValue()));
                case "orgUnitId" -> update.set(SyncPositionDO::getOrgUnitId, text(field.getValue()));
                case "status" -> update.set(SyncPositionDO::getStatus, upper(text(field.getValue())));
                case "sortNo" -> update.set(SyncPositionDO::getSortNo, asInt("sortNo", field.getValue()));
                default -> throw unsupportedField(SyncObjectType.POSITION, field.getKey());
            }
            settable = true;
        }
        if (!settable) {
            return existing;
        }
        update.set(SyncPositionDO::getUpdateAt, now())
                .set(SyncPositionDO::getUpdateBy, AUDIT_OPERATOR)
                .eq(SyncPositionDO::getId, row.getId());
        positionMapper.update(null, update);
        applyPosition(row, fields);
        return toPositionRecord(row);
    }

    private LandedRecord disablePosition(LandedRecord existing) {
        SyncPositionDO row = requirePosition(existing.localId());
        positionMapper.update(null, Wrappers.<SyncPositionDO>lambdaUpdate()
                .set(SyncPositionDO::getStatus, STATUS_DISABLED)
                .set(SyncPositionDO::getUpdateAt, now())
                .set(SyncPositionDO::getUpdateBy, AUDIT_OPERATOR)
                .eq(SyncPositionDO::getId, row.getId()));
        row.setStatus(STATUS_DISABLED);
        return toPositionRecord(row);
    }

    private static void applyPosition(SyncPositionDO row, Map<String, Object> fields) {
        for (Map.Entry<String, Object> field : fields.entrySet()) {
            switch (field.getKey()) {
                case "code" -> row.setCode(text(field.getValue()));
                case "name" -> row.setName(text(field.getValue()));
                case "orgUnitId" -> row.setOrgUnitId(text(field.getValue()));
                case "status" -> row.setStatus(upper(text(field.getValue())));
                case "sortNo" -> row.setSortNo(asInt("sortNo", field.getValue()));
                default -> throw unsupportedField(SyncObjectType.POSITION, field.getKey());
            }
        }
    }

    // ------------------------------------------------------------------ 外部身份

    private LandedRecord createExternalIdentity(SyncKey key, Map<String, Object> fields) {
        assertIdentitySource(key.sourceType());
        SyncExternalIdentityDO row = new SyncExternalIdentityDO();
        row.setId(Ids.newId(EXTERNAL_IDENTITY_PREFIX));
        row.setSource(key.sourceType());
        row.setExternalId(key.externalId());
        applyExternalIdentity(row, fields);
        if (row.getLinkedAt() == null) {
            row.setLinkedAt(now());
        }
        markAudit(row);
        externalIdentityMapper.insert(row);
        return toExternalIdentityRecord(row);
    }

    private LandedRecord updateExternalIdentity(LandedRecord existing, Map<String, Object> fields) {
        SyncExternalIdentityDO row = requireExternalIdentity(existing.localId());
        LambdaUpdateWrapper<SyncExternalIdentityDO> update = Wrappers.<SyncExternalIdentityDO>lambdaUpdate();
        boolean settable = false;
        for (Map.Entry<String, Object> field : fields.entrySet()) {
            switch (field.getKey()) {
                case "personId" -> update.set(SyncExternalIdentityDO::getPersonId, text(field.getValue()));
                case "linkMode" -> update.set(SyncExternalIdentityDO::getLinkMode, linkMode(field.getValue()));
                case "linkedAt" ->
                        update.set(SyncExternalIdentityDO::getLinkedAt, asDateTime("linkedAt", field.getValue()));
                default -> throw unsupportedField(SyncObjectType.EXTERNAL_IDENTITY, field.getKey());
            }
            settable = true;
        }
        if (!settable) {
            return existing;
        }
        update.set(SyncExternalIdentityDO::getUpdateAt, now())
                .set(SyncExternalIdentityDO::getUpdateBy, AUDIT_OPERATOR)
                .eq(SyncExternalIdentityDO::getId, row.getId());
        externalIdentityMapper.update(null, update);
        applyExternalIdentity(row, fields);
        return toExternalIdentityRecord(row);
    }

    /** 外部身份无状态列，「删除」即逻辑删（{@code @TableLogic}）。 */
    private LandedRecord disableExternalIdentity(LandedRecord existing) {
        SyncExternalIdentityDO row = requireExternalIdentity(existing.localId());
        externalIdentityMapper.deleteById(row.getId());
        return new LandedRecord(row.getId(), new SyncKey(row.getSource(), row.getExternalId()), 0L,
                externalIdentityFields(row), true, null);
    }

    private static void applyExternalIdentity(SyncExternalIdentityDO row, Map<String, Object> fields) {
        for (Map.Entry<String, Object> field : fields.entrySet()) {
            switch (field.getKey()) {
                case "personId" -> row.setPersonId(text(field.getValue()));
                case "linkMode" -> row.setLinkMode(linkMode(field.getValue()));
                case "linkedAt" -> row.setLinkedAt(asDateTime("linkedAt", field.getValue()));
                default -> throw unsupportedField(SyncObjectType.EXTERNAL_IDENTITY, field.getKey());
            }
        }
    }

    private static void assertIdentitySource(String sourceType) {
        if (sourceType == null || !IDENTITY_SOURCES.contains(sourceType)) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "外部身份来源不在允许集合内：" + sourceType + "，允许 " + IDENTITY_SOURCES);
        }
    }

    private static String linkMode(Object value) {
        String mode = upper(text(value));
        if (mode != null && !LINK_MODES.contains(mode)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "外部身份关联方式非法：" + mode);
        }
        return mode;
    }

    // ------------------------------------------------------------------ 记录投影

    private static LandedRecord toPersonRecord(SyncPersonDO row) {
        return new LandedRecord(row.getId(), new SyncKey(row.getSource(), row.getExternalId()),
                versionOf(row.getVersion()), personFields(row),
                STATUS_DISABLED.equalsIgnoreCase(row.getStatus()), null);
    }

    private static LandedRecord toOrgUnitRecord(SyncOrgUnitDO row, String parentExternalId) {
        return new LandedRecord(row.getId(), new SyncKey(row.getSource(), row.getExternalId()),
                versionOf(row.getVersion()), orgUnitFields(row),
                STATUS_DISABLED.equalsIgnoreCase(row.getStatus()), parentExternalId);
    }

    private static LandedRecord toPositionRecord(SyncPositionDO row) {
        return new LandedRecord(row.getId(), new SyncKey(row.getSource(), row.getExternalId()), 0L,
                positionFields(row), STATUS_DISABLED.equalsIgnoreCase(row.getStatus()), null);
    }

    private static LandedRecord toExternalIdentityRecord(SyncExternalIdentityDO row) {
        return new LandedRecord(row.getId(), new SyncKey(row.getSource(), row.getExternalId()), 0L,
                externalIdentityFields(row), false, null);
    }

    private static Map<String, Object> personFields(SyncPersonDO row) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("empNo", row.getEmpNo());
        fields.put("name", row.getName());
        fields.put("type", row.getType());
        fields.put("status", row.getStatus());
        fields.put("mobileCipher", row.getMobileCipher());
        fields.put("emailCipher", row.getEmailCipher());
        fields.put("mobileHash", row.getMobileHash());
        fields.put("emailHash", row.getEmailHash());
        return fields;
    }

    private static Map<String, Object> orgUnitFields(SyncOrgUnitDO row) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("code", row.getCode());
        fields.put("name", row.getName());
        fields.put("type", row.getType());
        fields.put("sortNo", row.getSortNo());
        fields.put("leaderPersonId", row.getLeaderPersonId());
        fields.put("effectiveAt", row.getEffectiveAt() == null ? null : row.getEffectiveAt().toString());
        fields.put("status", row.getStatus());
        return fields;
    }

    private static Map<String, Object> positionFields(SyncPositionDO row) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("code", row.getCode());
        fields.put("name", row.getName());
        fields.put("orgUnitId", row.getOrgUnitId());
        fields.put("status", row.getStatus());
        fields.put("sortNo", row.getSortNo());
        return fields;
    }

    private static Map<String, Object> externalIdentityFields(SyncExternalIdentityDO row) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("personId", row.getPersonId());
        fields.put("linkMode", row.getLinkMode());
        fields.put("linkedAt", row.getLinkedAt() == null ? null : row.getLinkedAt().toString());
        return fields;
    }

    // ------------------------------------------------------------------ 查询辅助

    private SyncPersonDO requirePerson(String localId) {
        SyncPersonDO row = personMapper.selectById(localId);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "人员不存在：" + localId);
        }
        return row;
    }

    private SyncOrgUnitDO requireOrgUnit(String localId) {
        SyncOrgUnitDO row = orgUnitMapper.selectById(localId);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "组织单元不存在：" + localId);
        }
        return row;
    }

    private SyncOrgUnitDO requireOrgUnitByKey(SyncKey key) {
        SyncOrgUnitDO row = orgUnitMapper.selectOne(Wrappers.<SyncOrgUnitDO>lambdaQuery()
                .eq(SyncOrgUnitDO::getSource, key.sourceType())
                .eq(SyncOrgUnitDO::getExternalId, key.externalId()));
        if (row == null) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "上级组织单元尚未落地：" + key.sourceType() + "/" + key.externalId());
        }
        return row;
    }

    private SyncPositionDO requirePosition(String localId) {
        SyncPositionDO row = positionMapper.selectById(localId);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "岗位不存在：" + localId);
        }
        return row;
    }

    private SyncExternalIdentityDO requireExternalIdentity(String localId) {
        SyncExternalIdentityDO row = externalIdentityMapper.selectById(localId);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "外部身份不存在：" + localId);
        }
        return row;
    }

    private String resolveParentExternalId(String parentId) {
        if (parentId == null || parentId.isBlank()) {
            return null;
        }
        SyncOrgUnitDO parent = orgUnitMapper.selectById(parentId);
        if (parent == null) {
            return null;
        }
        return parent.getExternalId() == null ? parent.getId() : parent.getExternalId();
    }

    // ------------------------------------------------------------------ 通用工具

    private static boolean identifiable(SyncPersonDO row) {
        return identifiable(row.getSource(), row.getExternalId());
    }

    private static boolean identifiable(String source, String externalId) {
        return source != null && !source.isBlank() && externalId != null && !externalId.isBlank();
    }

    private static long versionOf(Long version) {
        return version == null ? 0L : version;
    }

    private static int depthOf(SyncOrgUnitDO row) {
        return row.getDepth() == null ? 1 : row.getDepth();
    }

    private static String pathOf(String orgUnitId) {
        return "/" + orgUnitId;
    }

    private static void markAudit(SyncPersonDO row) {
        row.setCreateBy(AUDIT_OPERATOR);
        row.setUpdateBy(AUDIT_OPERATOR);
    }

    private static void markAudit(SyncOrgUnitDO row) {
        row.setCreateBy(AUDIT_OPERATOR);
        row.setUpdateBy(AUDIT_OPERATOR);
    }

    private static void markAudit(SyncPositionDO row) {
        row.setCreateBy(AUDIT_OPERATOR);
        row.setUpdateBy(AUDIT_OPERATOR);
    }

    private static void markAudit(SyncExternalIdentityDO row) {
        row.setCreateBy(AUDIT_OPERATOR);
        row.setUpdateBy(AUDIT_OPERATOR);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private static Integer asInt(String field, Object value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "字段 " + field + " 不是整数：" + text);
        }
    }

    private static LocalDateTime asDateTime(String field, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            // 继续尝试其它常见形态
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(text), ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // 继续尝试日期形态
        }
        try {
            return LocalDate.parse(text).atStartOfDay();
        } catch (DateTimeParseException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "字段 " + field + " 不是合法时间：" + text);
        }
    }

    private static BizException unsupportedObject(SyncObjectType objectType) {
        return new BizException(ErrorCode.BAD_REQUEST,
                "对象类型暂不支持同步落地：" + objectType + "（当前支持 " + supported() + "）");
    }

    private static BizException unsupportedField(SyncObjectType objectType, String field) {
        return new BizException(ErrorCode.BAD_REQUEST,
                "不支持的内置字段：objectType=" + objectType + " field=" + field);
    }

    private static List<String> supported() {
        List<String> types = new ArrayList<>();
        types.add(SyncObjectType.PERSON.name());
        types.add(SyncObjectType.ORG_UNIT.name());
        types.add(SyncObjectType.POSITION.name());
        types.add(SyncObjectType.EXTERNAL_IDENTITY.name());
        return types;
    }
}
