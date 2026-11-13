package com.foundation.org.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import com.foundation.org.api.dto.ChangeAction;
import com.foundation.org.api.dto.ChangeEntityType;
import com.foundation.org.api.dto.ChangeSource;
import com.foundation.org.api.dto.OrgSource;
import com.foundation.org.api.dto.OrgStatus;
import com.foundation.org.api.dto.PageResult;
import com.foundation.org.api.dto.PositionCmd;
import com.foundation.org.api.dto.PositionView;
import com.foundation.org.domain.OrgIdPrefix;
import com.foundation.org.domain.Position;
import com.foundation.org.infrastructure.OrgChangeLogWriter;
import com.foundation.org.infrastructure.OrgUnitRepository;
import com.foundation.org.infrastructure.PositionRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 岗位应用服务（F-03 §5、§7.3）：增删改查，写变更历史。 */
public class PositionService {

    private final PositionRepository positions;
    private final OrgUnitRepository orgUnits;
    private final OrgChangeLogWriter changeLog;

    public PositionService(PositionRepository positions, OrgUnitRepository orgUnits,
                           OrgChangeLogWriter changeLog) {
        this.positions = positions;
        this.orgUnits = orgUnits;
        this.changeLog = changeLog;
    }

    @Transactional
    public PositionView create(PositionCmd cmd) {
        requireText(cmd.code(), "岗位编码必填");
        requireText(cmd.name(), "岗位名称必填");
        if (positions.existsByCode(cmd.code())) {
            throw new BizException(ErrorCode.CONFLICT, "岗位编码已存在：" + cmd.code());
        }
        String orgUnitId = blankToNull(cmd.orgUnitId());
        requireActiveOrgUnit(orgUnitId);
        Position position = new Position(Ids.newId(OrgIdPrefix.POSITION), cmd.code(), cmd.name(), orgUnitId,
                OrgStatus.ACTIVE, cmd.sortNo() == null ? 0 : cmd.sortNo(), OrgSource.LOCAL, null);
        positions.insert(position);
        changeLog.append(ChangeEntityType.POSITION, position.id(), ChangeAction.CREATE, null, summary(position),
                ChangeSource.MANUAL, null);
        return position.toView();
    }

    @Transactional
    public PositionView update(String id, PositionCmd cmd) {
        Position current = requirePosition(id);
        String code = cmd.code() == null ? current.code() : cmd.code();
        if (!code.equals(current.code()) && positions.existsByCode(code)) {
            throw new BizException(ErrorCode.CONFLICT, "岗位编码已存在：" + code);
        }
        String orgUnitId = cmd.orgUnitId() == null ? current.orgUnitId() : blankToNull(cmd.orgUnitId());
        requireActiveOrgUnit(orgUnitId);
        Position updated = new Position(id, code, cmd.name() == null ? current.name() : cmd.name(), orgUnitId,
                current.status(), cmd.sortNo() == null ? current.sortNo() : cmd.sortNo(),
                current.source(), current.externalId());
        positions.update(updated);
        changeLog.append(ChangeEntityType.POSITION, id, ChangeAction.UPDATE, summary(current), summary(updated),
                ChangeSource.MANUAL, null);
        return updated.toView();
    }

    /** 删除（逻辑删除，48-CS §4）。 */
    @Transactional
    public void delete(String id) {
        Position current = requirePosition(id);
        positions.deleteById(id);
        changeLog.append(ChangeEntityType.POSITION, id, ChangeAction.DISABLE, summary(current), null,
                ChangeSource.MANUAL, null);
    }

    public PageResult<PositionView> list(int page, int size) {
        List<PositionView> items = positions.list(page, size).stream().map(Position::toView).toList();
        return PageResult.of(items, positions.count(), page, size);
    }

    public List<PositionView> listByOrgUnit(String orgUnitId) {
        return positions.findByOrgUnit(orgUnitId).stream().map(Position::toView).toList();
    }

    private Position requirePosition(String id) {
        return positions.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "岗位不存在：" + id));
    }

    private void requireActiveOrgUnit(String orgUnitId) {
        if (orgUnitId == null) {
            return;
        }
        boolean active = orgUnits.findById(orgUnitId).map(unit -> unit.active()).orElse(false);
        if (!active) {
            throw new BizException(ErrorCode.CONFLICT, "所属组织单元不存在或已停用：" + orgUnitId);
        }
    }

    private static Map<String, Object> summary(Position position) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", position.code());
        map.put("name", position.name());
        map.put("orgUnitId", position.orgUnitId());
        map.put("status", position.status().name());
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
