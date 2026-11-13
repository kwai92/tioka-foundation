package com.foundation.org.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import com.foundation.org.api.dto.ChangeAction;
import com.foundation.org.api.dto.ChangeEntityType;
import com.foundation.org.api.dto.ChangeSource;
import com.foundation.org.api.dto.EmploymentCmd;
import com.foundation.org.api.dto.EmploymentView;
import com.foundation.org.domain.Employment;
import com.foundation.org.domain.EmploymentRule;
import com.foundation.org.domain.OrgIdPrefix;
import com.foundation.org.domain.OrgUnit;
import com.foundation.org.domain.Person;
import com.foundation.org.infrastructure.EmploymentRepository;
import com.foundation.org.infrastructure.OrgChangeLogWriter;
import com.foundation.org.infrastructure.OrgEventOutbox;
import com.foundation.org.infrastructure.OrgUnitRepository;
import com.foundation.org.infrastructure.PersonRepository;
import com.foundation.org.infrastructure.PositionRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任职应用服务（F-03 §5、§7.3）：主任职与兼职的写入与切换。
 *
 * <p>置为主任职时，原有未结束主任职在同一事务内结束（{@link EmploymentRule}），保证原子性；
 * 任职变化经 {@code fdn.person.updated} 通知订阅方（调岗）。
 */
public class EmploymentService {

    private final EmploymentRepository employments;
    private final PersonRepository persons;
    private final OrgUnitRepository orgUnits;
    private final PositionRepository positions;
    private final EmploymentRule rule;
    private final OrgChangeLogWriter changeLog;
    private final OrgEventOutbox events;

    public EmploymentService(EmploymentRepository employments, PersonRepository persons,
                             OrgUnitRepository orgUnits, PositionRepository positions, EmploymentRule rule,
                             OrgChangeLogWriter changeLog, OrgEventOutbox events) {
        this.employments = employments;
        this.persons = persons;
        this.orgUnits = orgUnits;
        this.positions = positions;
        this.rule = rule;
        this.changeLog = changeLog;
        this.events = events;
    }

    /** 人员的任职（含历史）。 */
    public List<EmploymentView> list(String personId) {
        requirePerson(personId);
        return employments.byPerson(personId).stream().map(Employment::toView).toList();
    }

    @Transactional
    public EmploymentView create(String personId, EmploymentCmd cmd) {
        Person person = requirePerson(personId);
        requireActiveOrgUnit(cmd.orgUnitId());
        requirePosition(cmd.positionId());
        rule.assertDateRange(cmd.startDate(), cmd.endDate());

        Employment created = new Employment(Ids.newId(OrgIdPrefix.EMPLOYMENT), personId, cmd.orgUnitId(),
                blankToNull(cmd.positionId()), cmd.primary(), cmd.startDate(), cmd.endDate());
        if (created.open() && created.primary()) {
            closeSwitchedPrimary(personId, null, cmd.startDate());
        }
        employments.insert(created);
        changeLog.append(ChangeEntityType.EMPLOYMENT, created.id(), ChangeAction.CREATE, null,
                summary(created), ChangeSource.MANUAL, null);
        touchPerson(person);
        return created.toView();
    }

    @Transactional
    public EmploymentView update(String personId, String employmentId, EmploymentCmd cmd) {
        Person person = requirePerson(personId);
        Employment current = requireEmployment(personId, employmentId);
        requireActiveOrgUnit(cmd.orgUnitId());
        requirePosition(cmd.positionId());
        rule.assertDateRange(cmd.startDate(), cmd.endDate());

        Employment updated = new Employment(employmentId, personId, cmd.orgUnitId(),
                blankToNull(cmd.positionId()), cmd.primary(), cmd.startDate(), cmd.endDate());
        if (updated.open() && updated.primary()) {
            closeSwitchedPrimary(personId, employmentId, cmd.startDate());
        }
        employments.update(updated);
        changeLog.append(ChangeEntityType.EMPLOYMENT, employmentId, ChangeAction.UPDATE, summary(current),
                summary(updated), ChangeSource.MANUAL, null);
        touchPerson(person);
        return updated.toView();
    }

    /** 结束任职（保留历史，不物理删除，48-CS §4）；已结束则无操作。 */
    @Transactional
    public void delete(String personId, String employmentId) {
        Person person = requirePerson(personId);
        Employment current = requireEmployment(personId, employmentId);
        if (!current.open()) {
            return;
        }
        LocalDate endDate = LocalDate.now(ZoneOffset.UTC);
        if (endDate.isBefore(current.startDate())) {
            endDate = current.startDate();
        }
        employments.close(employmentId, endDate);
        changeLog.append(ChangeEntityType.EMPLOYMENT, employmentId, ChangeAction.DISABLE, summary(current),
                summary(current.closed(endDate)), ChangeSource.MANUAL, null);
        touchPerson(person);
    }

    /** 结束原未结束主任职（不含目标自身），与目标写入同事务。 */
    private void closeSwitchedPrimary(String personId, String targetEmploymentId, LocalDate endDate) {
        for (String id : rule.primarySwitchPlan(targetEmploymentId, employments.byPerson(personId))) {
            employments.close(id, endDate);
            changeLog.append(ChangeEntityType.EMPLOYMENT, id, ChangeAction.UPDATE, null, null,
                    ChangeSource.MANUAL, null);
        }
    }

    /** 任职变化即「调岗」：人员行版本 +1 并发出 {@code fdn.person.updated}（F-03 §6）。 */
    private void touchPerson(Person person) {
        long version = person.version() + 1;
        Person bumped = new Person(person.id(), person.empNo(), person.name(), person.type(), person.status(),
                person.mobileCipher(), person.emailCipher(), person.mobileHash(), person.emailHash(),
                person.source(), person.externalId(), version);
        persons.update(bumped);
        events.personUpdated(person.id(), version);
    }

    private Person requirePerson(String id) {
        return persons.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "人员不存在：" + id));
    }

    private Employment requireEmployment(String personId, String employmentId) {
        Employment employment = employments.findById(employmentId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "任职不存在：" + employmentId));
        if (!employment.personId().equals(personId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "任职不属于该人员：" + employmentId);
        }
        return employment;
    }

    private void requireActiveOrgUnit(String orgUnitId) {
        if (orgUnitId == null || orgUnitId.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "任职组织单元必填");
        }
        OrgUnit unit = orgUnits.findById(orgUnitId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "组织单元不存在：" + orgUnitId));
        if (!unit.active()) {
            throw new BizException(ErrorCode.CONFLICT, "组织单元已停用：" + orgUnitId);
        }
    }

    private void requirePosition(String positionId) {
        if (positionId != null && !positionId.isBlank() && positions.findById(positionId).isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "岗位不存在：" + positionId);
        }
    }

    private static Map<String, Object> summary(Employment employment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("personId", employment.personId());
        map.put("orgUnitId", employment.orgUnitId());
        map.put("positionId", employment.positionId());
        map.put("primary", employment.primary());
        map.put("startDate", employment.startDate() == null ? null : employment.startDate().toString());
        map.put("endDate", employment.endDate() == null ? null : employment.endDate().toString());
        return map;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
