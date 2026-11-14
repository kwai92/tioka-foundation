package com.foundation.org.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import com.foundation.org.api.dto.ChangeAction;
import com.foundation.org.api.dto.ChangeEntityType;
import com.foundation.org.api.dto.ChangeSource;
import com.foundation.org.api.dto.DigitalEmployeeCmd;
import com.foundation.org.api.dto.OrgChangeView;
import com.foundation.org.api.dto.OrgSource;
import com.foundation.org.api.dto.PageResult;
import com.foundation.org.api.dto.PersonCreateCmd;
import com.foundation.org.api.dto.PersonStatus;
import com.foundation.org.api.dto.PersonType;
import com.foundation.org.api.dto.PersonUpdateCmd;
import com.foundation.org.api.dto.PersonView;
import com.foundation.org.api.dto.ReportLineCmd;
import com.foundation.org.api.event.PersonDisabledEvent;
import com.foundation.org.domain.DigitalEmployeeRule;
import com.foundation.org.domain.Employment;
import com.foundation.org.domain.EmploymentRule;
import com.foundation.org.domain.OrgIdPrefix;
import com.foundation.org.domain.OrgUnit;
import com.foundation.org.domain.Person;
import com.foundation.org.domain.ReportLine;
import com.foundation.org.infrastructure.EmploymentRepository;
import com.foundation.org.infrastructure.OrgChangeLogWriter;
import com.foundation.org.infrastructure.OrgEventOutbox;
import com.foundation.org.infrastructure.OrgUnitRepository;
import com.foundation.org.infrastructure.PersonRepository;
import com.foundation.org.infrastructure.PositionRepository;
import com.foundation.org.infrastructure.ReportLineRepository;
import com.foundation.org.infrastructure.person.PersonCipher;
import com.foundation.org.infrastructure.person.PersonMasker;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 人员应用服务（F-03 §5、§7.3）：增删改、手机 / 邮箱加密与脱敏出参、数字员工档案
 * （按（{@code source}, {@code externalId}）幂等）、任职与汇报关系维护。
 */
public class PersonService {

    /** 变更历史默认返回条数。 */
    private static final int DEFAULT_CHANGE_LIMIT = 100;

    private final PersonRepository persons;
    private final EmploymentRepository employments;
    private final ReportLineRepository reportLines;
    private final OrgUnitRepository orgUnits;
    private final PositionRepository positions;
    private final PersonCipher cipher;
    private final PersonMasker masker;
    private final EmploymentRule employmentRule;
    private final DigitalEmployeeRule digitalEmployeeRule;
    private final OrgChangeLogWriter changeLog;
    private final OrgEventOutbox events;

    public PersonService(PersonRepository persons, EmploymentRepository employments,
                         ReportLineRepository reportLines, OrgUnitRepository orgUnits,
                         PositionRepository positions, PersonCipher cipher, PersonMasker masker,
                         EmploymentRule employmentRule, DigitalEmployeeRule digitalEmployeeRule,
                         OrgChangeLogWriter changeLog, OrgEventOutbox events) {
        this.persons = persons;
        this.employments = employments;
        this.reportLines = reportLines;
        this.orgUnits = orgUnits;
        this.positions = positions;
        this.cipher = cipher;
        this.masker = masker;
        this.employmentRule = employmentRule;
        this.digitalEmployeeRule = digitalEmployeeRule;
        this.changeLog = changeLog;
        this.events = events;
    }

    @Transactional
    public PersonView create(PersonCreateCmd cmd) {
        requireText(cmd.empNo(), "工号必填");
        requireText(cmd.name(), "姓名必填");
        if (persons.existsByEmpNo(cmd.empNo())) {
            throw new BizException(ErrorCode.CONFLICT, "工号已存在：" + cmd.empNo());
        }
        PersonType type = cmd.type() == null ? PersonType.EMPLOYEE : cmd.type();
        String mobileHash = cipher.hash(cmd.mobile());
        String emailHash = cipher.hash(cmd.email());
        assertContactAvailable(mobileHash, emailHash, null);

        String id = Ids.newId(OrgIdPrefix.PERSON);
        Person person = new Person(id, cmd.empNo(), cmd.name(), type, PersonStatus.ACTIVE,
                cipher.encrypt(cmd.mobile()), cipher.encrypt(cmd.email()), mobileHash, emailHash,
                OrgSource.LOCAL, null, 0L);
        persons.insert(person);
        changeLog.append(ChangeEntityType.PERSON, id, ChangeAction.CREATE, null, summary(person),
                ChangeSource.MANUAL, null);
        events.personCreated(id, 0L);
        return toView(person, true);
    }

    @Transactional
    public PersonView update(String id, PersonUpdateCmd cmd) {
        Person current = requirePerson(id);
        String mobileHash = current.mobileHash();
        String mobileCipher = current.mobileCipher();
        if (cmd.mobile() != null) {
            mobileHash = cipher.hash(cmd.mobile());
            mobileCipher = cipher.encrypt(cmd.mobile());
        }
        String emailHash = current.emailHash();
        String emailCipher = current.emailCipher();
        if (cmd.email() != null) {
            emailHash = cipher.hash(cmd.email());
            emailCipher = cipher.encrypt(cmd.email());
        }
        assertContactAvailable(
                Objects.equals(mobileHash, current.mobileHash()) ? null : mobileHash,
                Objects.equals(emailHash, current.emailHash()) ? null : emailHash, id);

        long version = current.version() + 1;
        Person updated = new Person(id, current.empNo(), cmd.name() == null ? current.name() : cmd.name(),
                cmd.type() == null ? current.type() : cmd.type(), current.status(), mobileCipher, emailCipher,
                mobileHash, emailHash, current.source(), current.externalId(), version);
        persons.update(updated);
        changeLog.append(ChangeEntityType.PERSON, id, ChangeAction.UPDATE, summary(current), summary(updated),
                ChangeSource.MANUAL, null);
        events.personUpdated(id, version);
        return toView(updated, true);
    }

    @Transactional
    public void disable(String id) {
        Person current = requirePerson(id);
        long version = current.version() + 1;
        Person disabled = new Person(id, current.empNo(), current.name(), current.type(), PersonStatus.DISABLED,
                current.mobileCipher(), current.emailCipher(), current.mobileHash(), current.emailHash(),
                current.source(), current.externalId(), version);
        persons.update(disabled);
        changeLog.append(ChangeEntityType.PERSON, id, ChangeAction.DISABLE, summary(current), summary(disabled),
                ChangeSource.MANUAL, null);
        events.personDisabled(id, version);
    }

    /**
     * 离职（F-04 BAS-14）：状态置 {@code LEFT}、写变更日志，发 {@code fdn.person.disabled}
     * （{@code changeType=LEFT}）。
     *
     * <p>幂等：已是 {@code LEFT} 时不再变更、不重复发事件；消费端仍按事件 ID 去重。
     */
    @Transactional
    public void offboard(String id, String operator) {
        Person current = requirePerson(id);
        if (current.status() == PersonStatus.LEFT) {
            return;
        }
        long version = current.version() + 1;
        Person left = new Person(id, current.empNo(), current.name(), current.type(), PersonStatus.LEFT,
                current.mobileCipher(), current.emailCipher(), current.mobileHash(), current.emailHash(),
                current.source(), current.externalId(), version);
        persons.update(left);
        changeLog.append(ChangeEntityType.PERSON, id, ChangeAction.DISABLE, summary(current), summary(left),
                ChangeSource.MANUAL, operator);
        events.personDisabled(id, version, PersonDisabledEvent.CHANGE_LEFT);
    }

    /**
     * 人员详情。
     *
     * @param revealSensitive 是否返回手机 / 邮箱明文（F-03 §4.2：详情按权限决定）
     */
    public PersonView detail(String id, boolean revealSensitive) {
        return toView(requirePerson(id), !revealSensitive);
    }

    /** 人员详情（明文，模块间内部边界用，{@code PersonApi} 口径）。 */
    public Optional<PersonView> findById(String id) {
        return persons.findById(id).map(person -> toView(person, false));
    }

    /** 人员列表 / 搜索（列表一律脱敏）。 */
    public PageResult<PersonView> search(String q, PersonType type, PersonStatus status,
                                         String orgUnitId, int page, int size) {
        Collection<String> restrict = restrictPersonIds(orgUnitId);
        List<PersonView> items = persons.search(q, type, status, restrict, page, size).stream()
                .map(person -> toView(person, true)).toList();
        return PageResult.of(items, persons.count(q, type, status, restrict), page, size);
    }

    /** 人员的变更历史。 */
    public List<OrgChangeView> changes(String id, Integer limit) {
        int size = limit == null || limit < 1 ? DEFAULT_CHANGE_LIMIT : limit;
        return changeLog.findChanges(ChangeEntityType.PERSON, id, size).stream()
                .map(entry -> entry.toView()).toList();
    }

    /**
     * 数字员工档案维护（F-03 §3、§5 {@code PUT /api/fdn/persons/by-external/{source}/{externalId}}）。
     *
     * <p>上岗（{@code ACTIVE}）→ 建 / 更人员 + 主任职 + 汇报关系，发 {@code fdn.person.created|updated}；
     * 下岗（{@code DISABLED}）→ 状态置 {@code DISABLED}，发 {@code fdn.person.disabled}。
     */
    @Transactional
    public PersonView maintainDigitalEmployee(String source, String externalId, DigitalEmployeeCmd cmd) {
        requireText(source, "来源必填");
        requireText(externalId, "外部 ID 必填");
        if (cmd == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请求体必填");
        }
        digitalEmployeeRule.assertOwnerProvided(cmd.ownerPersonId());
        Person owner = requirePerson(cmd.ownerPersonId());
        Person existing = persons.findBySourceExternalId(source, externalId).orElse(null);

        if (cmd.status() == PersonStatus.DISABLED) {
            return offboardDigitalEmployee(existing, source, externalId);
        }
        return onboardDigitalEmployee(existing, source, externalId, cmd, owner);
    }

    /** 维护直属上级（F-03 §5）；{@code managerPersonId} 为空表示清除。 */
    @Transactional
    public void updateReportLine(String personId, ReportLineCmd cmd) {
        requirePerson(personId);
        String managerId = cmd == null ? null : blankToNull(cmd.managerPersonId());
        Optional<String> before = reportLines.managerOf(personId);
        if (managerId == null) {
            if (before.isPresent()) {
                reportLines.deleteByPersonId(personId);
                changeLog.append(ChangeEntityType.REPORT_LINE, personId, ChangeAction.DISABLE,
                        managerSummary(before.get()), null, ChangeSource.MANUAL, null);
            }
            return;
        }
        if (managerId.equals(personId)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "直属上级不能是本人");
        }
        requirePerson(managerId);
        reportLines.upsert(new ReportLine(personId, managerId, ReportLine.SOURCE_LOCAL));
        changeLog.append(ChangeEntityType.REPORT_LINE, personId,
                before.isPresent() ? ChangeAction.UPDATE : ChangeAction.CREATE,
                before.map(PersonService::managerSummary).orElse(null), managerSummary(managerId),
                ChangeSource.MANUAL, null);
    }

    private PersonView onboardDigitalEmployee(Person existing, String source, String externalId,
                                              DigitalEmployeeCmd cmd, Person owner) {
        digitalEmployeeRule.assertPrimaryEmployment(true);
        requireText(cmd.orgUnitId(), "数字员工必须指定服务部门（departmentId）");
        OrgUnit unit = orgUnits.findById(cmd.orgUnitId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "组织单元不存在：" + cmd.orgUnitId()));
        if (!unit.active()) {
            throw new BizException(ErrorCode.CONFLICT, "数字员工服务部门已停用：" + cmd.orgUnitId());
        }
        String positionId = blankToNull(cmd.positionId());
        if (positionId != null && positions.findById(positionId).isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "岗位不存在：" + positionId);
        }

        Person person;
        if (existing == null) {
            requireText(cmd.empNo(), "工号必填");
            if (persons.existsByEmpNo(cmd.empNo())) {
                throw new BizException(ErrorCode.CONFLICT, "工号已存在：" + cmd.empNo());
            }
            person = new Person(Ids.newId(OrgIdPrefix.PERSON), cmd.empNo(), cmd.name(),
                    PersonType.DIGITAL_EMPLOYEE, PersonStatus.ACTIVE, null, null, null, null,
                    source, externalId, 0L);
            persons.insert(person);
            changeLog.append(ChangeEntityType.PERSON, person.id(), ChangeAction.CREATE, null, summary(person),
                    ChangeSource.EVENT, null);
            events.personCreated(person.id(), 0L);
        } else {
            long version = existing.version() + 1;
            person = new Person(existing.id(), existing.empNo(), cmd.name() == null ? existing.name() : cmd.name(),
                    PersonType.DIGITAL_EMPLOYEE, PersonStatus.ACTIVE, existing.mobileCipher(), existing.emailCipher(),
                    existing.mobileHash(), existing.emailHash(), source, externalId, version);
            persons.update(person);
            changeLog.append(ChangeEntityType.PERSON, person.id(), ChangeAction.UPDATE, summary(existing),
                    summary(person), ChangeSource.EVENT, null);
            events.personUpdated(person.id(), version);
        }

        maintainPrimaryEmployment(person.id(), unit.id(), positionId);
        maintainOwner(person.id(), owner.id());
        return toView(person, true);
    }

    private PersonView offboardDigitalEmployee(Person existing, String source, String externalId) {
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "数字员工档案不存在：" + source + "/" + externalId);
        }
        long version = existing.version() + 1;
        Person offboarded = new Person(existing.id(), existing.empNo(), existing.name(),
                PersonType.DIGITAL_EMPLOYEE, digitalEmployeeRule.statusOnOffboarding(), existing.mobileCipher(),
                existing.emailCipher(), existing.mobileHash(), existing.emailHash(), source, externalId, version);
        persons.update(offboarded);
        changeLog.append(ChangeEntityType.PERSON, offboarded.id(), ChangeAction.DISABLE, summary(existing),
                summary(offboarded), ChangeSource.EVENT, null);
        events.personDisabled(offboarded.id(), version);
        return toView(offboarded, true);
    }

    /** 主任职就位：已指向同一部门 / 岗位则幂等，否则结束原主任职并在同事务内新挂一条。 */
    private void maintainPrimaryEmployment(String personId, String orgUnitId, String positionId) {
        LocalDate start = LocalDate.now(ZoneOffset.UTC);
        Optional<Employment> primary = employments.openPrimary(personId);
        if (primary.isPresent() && Objects.equals(primary.get().orgUnitId(), orgUnitId)
                && Objects.equals(primary.get().positionId(), positionId)) {
            return;
        }
        for (String closed : employmentRule.primarySwitchPlan(null, employments.byPerson(personId))) {
            employments.close(closed, start);
            changeLog.append(ChangeEntityType.EMPLOYMENT, closed, ChangeAction.DISABLE, null, null,
                    ChangeSource.EVENT, null);
        }
        Employment created = new Employment(Ids.newId(OrgIdPrefix.EMPLOYMENT), personId, orgUnitId, positionId,
                true, start, null);
        employments.insert(created);
        changeLog.append(ChangeEntityType.EMPLOYMENT, created.id(), ChangeAction.CREATE, null,
                employmentSummary(created), ChangeSource.EVENT, null);
    }

    private void maintainOwner(String personId, String ownerPersonId) {
        Optional<String> before = reportLines.managerOf(personId);
        if (before.isPresent() && before.get().equals(ownerPersonId)) {
            return;
        }
        reportLines.upsert(new ReportLine(personId, ownerPersonId, ReportLine.SOURCE_LOCAL));
        changeLog.append(ChangeEntityType.REPORT_LINE, personId,
                before.isPresent() ? ChangeAction.UPDATE : ChangeAction.CREATE,
                before.map(PersonService::managerSummary).orElse(null), managerSummary(ownerPersonId),
                ChangeSource.EVENT, null);
    }

    private Collection<String> restrictPersonIds(String orgUnitId) {
        if (orgUnitId == null || orgUnitId.isBlank()) {
            return null;
        }
        Set<String> subtree = orgUnits.descendantIds(orgUnitId);
        return subtree.isEmpty() ? Set.of() : employments.openPersonIdsInOrgUnits(subtree);
    }

    private PersonView toView(Person person, boolean masked) {
        String mobile = cipher.decrypt(person.mobileCipher());
        String email = cipher.decrypt(person.emailCipher());
        if (masked) {
            mobile = masker.maskMobile(mobile);
            email = masker.maskEmail(email);
        }
        String primaryOrgUnitId = employments.openPrimary(person.id())
                .map(Employment::orgUnitId).orElse(null);
        String managerPersonId = reportLines.managerOf(person.id()).orElse(null);
        return person.toView(mobile, email, masked, primaryOrgUnitId, managerPersonId);
    }

    private void assertContactAvailable(String mobileHash, String emailHash, String excludePersonId) {
        if (mobileHash != null && persons.existsByMobileHash(mobileHash, excludePersonId)) {
            throw new BizException(ErrorCode.CONFLICT, "手机号已被占用");
        }
        if (emailHash != null && persons.existsByEmailHash(emailHash, excludePersonId)) {
            throw new BizException(ErrorCode.CONFLICT, "邮箱已被占用");
        }
    }

    private Person requirePerson(String id) {
        return persons.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "人员不存在：" + id));
    }

    private static Map<String, Object> summary(Person person) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("empNo", person.empNo());
        map.put("name", person.name());
        map.put("type", person.type().name());
        map.put("status", person.status().name());
        map.put("source", person.source());
        map.put("externalId", person.externalId());
        return map;
    }

    private static Map<String, Object> employmentSummary(Employment employment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("personId", employment.personId());
        map.put("orgUnitId", employment.orgUnitId());
        map.put("positionId", employment.positionId());
        map.put("primary", employment.primary());
        map.put("startDate", employment.startDate() == null ? null : employment.startDate().toString());
        map.put("endDate", employment.endDate() == null ? null : employment.endDate().toString());
        return map;
    }

    private static Map<String, Object> managerSummary(String managerPersonId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("managerPersonId", managerPersonId);
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
