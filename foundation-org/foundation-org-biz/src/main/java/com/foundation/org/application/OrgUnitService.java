package com.foundation.org.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import com.foundation.org.api.dto.ChangeAction;
import com.foundation.org.api.dto.ChangeEntityType;
import com.foundation.org.api.dto.ChangeSource;
import com.foundation.org.api.dto.OrgChangeView;
import com.foundation.org.api.dto.OrgSource;
import com.foundation.org.api.dto.OrgStatus;
import com.foundation.org.api.dto.OrgUnitCreateCmd;
import com.foundation.org.api.dto.OrgUnitTreeNode;
import com.foundation.org.api.dto.OrgUnitType;
import com.foundation.org.api.dto.OrgUnitUpdateCmd;
import com.foundation.org.api.dto.OrgUnitView;
import com.foundation.org.api.dto.PageResult;
import com.foundation.org.api.event.OrgChangedEvent;
import com.foundation.org.domain.OrgChangeLogEntry;
import com.foundation.org.domain.OrgIdPrefix;
import com.foundation.org.domain.OrgTreePolicy;
import com.foundation.org.domain.OrgUnit;
import com.foundation.org.infrastructure.EmploymentRepository;
import com.foundation.org.infrastructure.OrgChangeLogWriter;
import com.foundation.org.infrastructure.OrgEventOutbox;
import com.foundation.org.infrastructure.OrgUnitRepository;
import com.foundation.org.infrastructure.PositionRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 组织单元应用服务（F-03 §7.3）：建 / 改 / 移动 / 停用，维护物化路径与深度，
 * 写变更历史并经 outbox 发 {@code fdn.org.changed}；同时提供组织树与列表查询。
 */
public class OrgUnitService {

    /** 变更历史默认返回条数。 */
    private static final int DEFAULT_CHANGE_LIMIT = 100;

    private final OrgUnitRepository orgUnits;
    private final PositionRepository positions;
    private final EmploymentRepository employments;
    private final OrgTreePolicy treePolicy;
    private final OrgChangeLogWriter changeLog;
    private final OrgEventOutbox events;

    public OrgUnitService(OrgUnitRepository orgUnits, PositionRepository positions,
                          EmploymentRepository employments, OrgTreePolicy treePolicy,
                          OrgChangeLogWriter changeLog, OrgEventOutbox events) {
        this.orgUnits = orgUnits;
        this.positions = positions;
        this.employments = employments;
        this.treePolicy = treePolicy;
        this.changeLog = changeLog;
        this.events = events;
    }

    @Transactional
    public OrgUnitView create(OrgUnitCreateCmd cmd) {
        requireText(cmd.code(), "组织单元编码必填");
        requireText(cmd.name(), "组织单元名称必填");
        if (cmd.type() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "组织单元类型必填");
        }
        if (orgUnits.existsByCode(cmd.code())) {
            throw new BizException(ErrorCode.CONFLICT, "组织单元编码已存在：" + cmd.code());
        }
        OrgUnit parent = resolveParent(cmd.parentId());
        String id = Ids.newId(OrgIdPrefix.ORG_UNIT);
        String path = parent == null ? treePolicy.rootPath(id) : treePolicy.childPath(parent.path(), id);
        int depth = treePolicy.depthOfChild(parent == null ? null : parent.path());
        treePolicy.assertDepthWithinLimit(depth);

        OrgUnit unit = new OrgUnit(id, cmd.code(), cmd.name(), cmd.type(),
                parent == null ? null : parent.id(), path, depth, cmd.sortNo() == null ? 0 : cmd.sortNo(),
                blankToNull(cmd.leaderPersonId()), cmd.effectiveAt(), OrgStatus.ACTIVE, OrgSource.LOCAL, null, 0L);
        orgUnits.insert(unit);
        changeLog.append(ChangeEntityType.ORG_UNIT, id, ChangeAction.CREATE, null, summary(unit),
                ChangeSource.MANUAL, null);
        events.orgChanged(id, 0L, OrgChangedEvent.CHANGE_CREATED);
        return unit.toView();
    }

    @Transactional
    public OrgUnitView update(String id, OrgUnitUpdateCmd cmd) {
        OrgUnit current = requireOrgUnit(id);
        OrgUnit parent = resolveParent(cmd.parentId());
        String newParentId = parent == null ? null : parent.id();
        boolean moved = !Objects.equals(newParentId, current.parentId());

        String newPath = current.path();
        int newDepth = current.depth();
        if (moved) {
            if (parent == null) {
                newPath = treePolicy.rootPath(id);
                newDepth = 1;
            } else {
                Set<String> subtree = orgUnits.descendantIds(id);
                treePolicy.assertNoCycle(id, parent.id(), subtree);
                newPath = treePolicy.childPath(parent.path(), id);
                newDepth = treePolicy.depthOfChild(parent.path());
            }
            treePolicy.assertDepthWithinLimit(newDepth);
        }

        OrgUnitType type = cmd.type() == null ? current.type() : cmd.type();
        String name = cmd.name() == null ? current.name() : cmd.name();
        int sortNo = cmd.sortNo() == null ? current.sortNo() : cmd.sortNo();
        String leader = cmd.leaderPersonId() == null ? current.leaderPersonId() : blankToNull(cmd.leaderPersonId());
        Instant effectiveAt = cmd.effectiveAt() == null ? current.effectiveAt() : cmd.effectiveAt();
        long version = current.version() + 1;

        OrgUnit updated = new OrgUnit(id, current.code(), name, type, newParentId, newPath, newDepth, sortNo,
                leader, effectiveAt, current.status(), current.source(), current.externalId(), version);
        orgUnits.update(updated);
        if (moved) {
            orgUnits.rewriteSubtree(current.path() + OrgTreePolicy.SEPARATOR,
                    newPath + OrgTreePolicy.SEPARATOR, newDepth - current.depth());
        }
        changeLog.append(ChangeEntityType.ORG_UNIT, id, moved ? ChangeAction.MOVE : ChangeAction.UPDATE,
                summary(current), summary(updated), ChangeSource.MANUAL, null);
        events.orgChanged(id, version,
                moved ? OrgChangedEvent.CHANGE_MOVED : OrgChangedEvent.CHANGE_UPDATED);
        return updated.toView();
    }

    /** 停用：校验下级与在岗人员已迁出（F-03 §2.1）。 */
    @Transactional
    public void disable(String id) {
        OrgUnit current = requireOrgUnit(id);
        boolean hasChildren = !orgUnits.childrenOf(id).isEmpty();
        long activeEmploymentCount = employments.countOpenByOrgUnit(id);
        treePolicy.assertDeactivatable(hasChildren, activeEmploymentCount);

        long version = current.version() + 1;
        OrgUnit disabled = new OrgUnit(id, current.code(), current.name(), current.type(), current.parentId(),
                current.path(), current.depth(), current.sortNo(), current.leaderPersonId(), current.effectiveAt(),
                OrgStatus.DISABLED, current.source(), current.externalId(), version);
        orgUnits.update(disabled);
        changeLog.append(ChangeEntityType.ORG_UNIT, id, ChangeAction.DISABLE, summary(current), summary(disabled),
                ChangeSource.MANUAL, null);
        events.orgChanged(id, version, OrgChangedEvent.CHANGE_DISABLED);
    }

    /** 组织单元详情。 */
    public OrgUnitView findById(String id) {
        return requireOrgUnit(id).toView();
    }

    /** 组织单元列表 / 搜索。 */
    public PageResult<OrgUnitView> search(String q, OrgUnitType type, OrgStatus status, int page, int size) {
        List<OrgUnitView> items = orgUnits.search(q, type, status, page, size).stream()
                .map(OrgUnit::toView).toList();
        return PageResult.of(items, orgUnits.count(q, type, status), page, size);
    }

    /**
     * 组织树（含岗位计数）。
     *
     * @param rootId          根节点 ID，空表示从全部根节点起
     * @param depth           展开层数上限，空表示不限制
     * @param includeDisabled 是否含停用节点
     */
    public List<OrgUnitTreeNode> tree(String rootId, Integer depth, boolean includeDisabled) {
        List<OrgUnit> units = rootId == null || rootId.isBlank()
                ? orgUnits.findAll() : orgUnits.subtreeOf(rootId);
        if (!includeDisabled) {
            units = units.stream().filter(OrgUnit::active).toList();
        }
        if (units.isEmpty()) {
            return List.of();
        }
        Map<String, List<OrgUnit>> byParent = new LinkedHashMap<>();
        for (OrgUnit unit : units) {
            byParent.computeIfAbsent(unit.parentId(), key -> new ArrayList<>()).add(unit);
        }
        Map<String, Long> positionCounts = positions.countByOrgUnit(units.stream().map(OrgUnit::id).toList());

        List<OrgUnit> roots = rootId == null || rootId.isBlank()
                ? byParent.getOrDefault(null, List.of())
                : units.stream().filter(unit -> unit.id().equals(rootId)).toList();
        return roots.stream()
                .map(root -> toNode(root, byParent, positionCounts, depth, 1))
                .toList();
    }

    /** 组织单元的变更历史。 */
    public List<OrgChangeView> changes(String id, Integer limit) {
        int size = limit == null || limit < 1 ? DEFAULT_CHANGE_LIMIT : limit;
        return changeLog.findChanges(ChangeEntityType.ORG_UNIT, id, size).stream()
                .map(OrgChangeLogEntry::toView).toList();
    }

    private OrgUnitTreeNode toNode(OrgUnit unit, Map<String, List<OrgUnit>> byParent,
                                   Map<String, Long> positionCounts, Integer maxDepth, int level) {
        List<OrgUnit> children = byParent.getOrDefault(unit.id(), List.of());
        List<OrgUnitTreeNode> childNodes = maxDepth != null && level >= maxDepth
                ? List.of()
                : children.stream().map(child -> toNode(child, byParent, positionCounts, maxDepth, level + 1)).toList();
        int positionCount = positionCounts.getOrDefault(unit.id(), 0L).intValue();
        return new OrgUnitTreeNode(unit.toView(), positionCount, children.size(), childNodes);
    }

    private OrgUnit requireOrgUnit(String id) {
        return orgUnits.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "组织单元不存在：" + id));
    }

    private OrgUnit resolveParent(String parentId) {
        if (parentId == null || parentId.isBlank()) {
            return null;
        }
        OrgUnit parent = requireOrgUnit(parentId);
        if (!parent.active()) {
            throw new BizException(ErrorCode.CONFLICT, "上级组织单元已停用：" + parentId);
        }
        return parent;
    }

    private static Map<String, Object> summary(OrgUnit unit) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", unit.code());
        map.put("name", unit.name());
        map.put("type", unit.type().name());
        map.put("parentId", unit.parentId());
        map.put("path", unit.path());
        map.put("depth", unit.depth());
        map.put("status", unit.status().name());
        return map;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, message);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
