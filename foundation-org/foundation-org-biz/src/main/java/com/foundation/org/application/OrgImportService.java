package com.foundation.org.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import com.foundation.org.api.dto.ChangeAction;
import com.foundation.org.api.dto.ChangeEntityType;
import com.foundation.org.api.dto.ChangeSource;
import com.foundation.org.api.dto.ImportError;
import com.foundation.org.api.dto.ImportResult;
import com.foundation.org.api.dto.OrgSource;
import com.foundation.org.api.dto.OrgStatus;
import com.foundation.org.api.dto.OrgUnitType;
import com.foundation.org.api.dto.PersonStatus;
import com.foundation.org.api.dto.PersonType;
import com.foundation.org.config.OrgImportProperties;
import com.foundation.org.domain.Employment;
import com.foundation.org.domain.EmploymentRule;
import com.foundation.org.domain.OrgIdPrefix;
import com.foundation.org.domain.OrgTreePolicy;
import com.foundation.org.domain.OrgUnit;
import com.foundation.org.domain.Person;
import com.foundation.org.domain.Position;
import com.foundation.org.infrastructure.EmploymentRepository;
import com.foundation.org.infrastructure.OrgChangeLogWriter;
import com.foundation.org.infrastructure.OrgUnitRepository;
import com.foundation.org.infrastructure.PersonRepository;
import com.foundation.org.infrastructure.PositionRepository;
import com.foundation.org.infrastructure.person.PersonCipher;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Excel 导入应用服务（F-03 §4.1、§7.3）。
 *
 * <p>流程：解析四个工作表 → 整表全量校验（任一行失败则整体拒绝、不半量落库）→ 按
 * {@code foundation.org.import.batch-size} 分批在同一事务内 upsert → 一条 {@code IMPORT} 变更历史。
 *
 * <p>业务键：组织单元按 {@code code}、岗位按 {@code code}、人员按 {@code emp_no}、任职按
 * {@code (person_id, org_unit_id, start_date)}；同一文件重复导入零新增、零更新（逐字段比对后跳过）。
 * 任务结果按 {@code taskId} 短期保留于内存，供 {@code GET /api/fdn/org/import/{taskId}} 查询。
 *
 * <p>模板 {@code v1} 列序：组织单元＝编码 / 名称 / 类型 / 上级编码 / 排序 / 负责人工号；
 * 岗位＝编码 / 名称 / 组织单元编码 / 排序；人员＝工号 / 姓名 / 类型 / 手机 / 邮箱；
 * 任职＝工号 / 组织单元编码 / 岗位编码 / 是否主任职 / 开始日期 / 结束日期。首行为表头。
 */
public class OrgImportService {

    private static final String SHEET_ORG_UNIT = "组织单元";
    private static final String SHEET_POSITION = "岗位";
    private static final String SHEET_PERSON = "人员";
    private static final String SHEET_EMPLOYMENT = "任职";

    /** 任务结果保留上限（超出按插入顺序淘汰）。 */
    private static final int MAX_RETAINED_TASKS = 100;

    private final OrgUnitRepository orgUnits;
    private final PositionRepository positions;
    private final PersonRepository persons;
    private final EmploymentRepository employments;
    private final PersonCipher cipher;
    private final OrgTreePolicy treePolicy;
    private final EmploymentRule employmentRule;
    private final OrgChangeLogWriter changeLog;
    private final OrgImportProperties properties;

    private final Map<String, ImportResult> tasks = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ImportResult> eldest) {
            return size() > MAX_RETAINED_TASKS;
        }
    });

    public OrgImportService(OrgUnitRepository orgUnits, PositionRepository positions, PersonRepository persons,
                            EmploymentRepository employments, PersonCipher cipher, OrgTreePolicy treePolicy,
                            EmploymentRule employmentRule, OrgChangeLogWriter changeLog,
                            OrgImportProperties properties) {
        this.orgUnits = orgUnits;
        this.positions = positions;
        this.persons = persons;
        this.employments = employments;
        this.cipher = cipher;
        this.treePolicy = treePolicy;
        this.employmentRule = employmentRule;
        this.changeLog = changeLog;
        this.properties = properties;
    }

    /** 导入任务结果查询。 */
    public Optional<ImportResult> result(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    @Transactional
    public ImportResult importWorkbook(InputStream input) {
        String taskId = Ids.newId(OrgIdPrefix.IMPORT_TASK);
        List<ImportError> errors = new ArrayList<>();
        SheetContext context;
        try (Workbook workbook = WorkbookFactory.create(input)) {
            context = read(workbook, errors);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "导入文件无法解析：" + ex.getMessage());
        }

        int dataRows = context.totalRows();
        if (dataRows > properties.getMaxRows()) {
            errors.add(new ImportError("文件", 0,
                    "数据行数 " + dataRows + " 超过上限 " + properties.getMaxRows()));
        }
        Map<String, Integer> orgDepth = validate(context, errors);
        if (!errors.isEmpty()) {
            ImportResult rejected = new ImportResult(taskId, 0, 0, 0, List.copyOf(errors));
            tasks.put(taskId, rejected);
            return rejected;
        }
        context = context.withOrgDepth(orgDepth);

        Counts counts = new Counts();
        applyPersons(context.personRows(), counts);
        Map<String, String> orgUnitIds = applyOrgUnits(context, counts);
        Map<String, String> positionIds = applyPositions(context.positionRows(), counts);
        applyEmployments(context.employmentRows(), orgUnitIds, positionIds, counts);

        ImportResult result = new ImportResult(taskId, counts.created, counts.updated, counts.skipped, List.of());
        tasks.put(taskId, result);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("created", counts.created);
        summary.put("updated", counts.updated);
        summary.put("skipped", counts.skipped);
        changeLog.append(ChangeEntityType.ORG_UNIT, taskId, ChangeAction.IMPORT, null, summary,
                ChangeSource.IMPORT, null);
        return result;
    }

    // ---------------------------------------------------------------- 读取

    private SheetContext read(Workbook workbook, List<ImportError> errors) {
        return new SheetContext(
                readOrgRows(workbook.getSheet(SHEET_ORG_UNIT)),
                readPositionRows(workbook.getSheet(SHEET_POSITION)),
                readPersonRows(workbook.getSheet(SHEET_PERSON)),
                readEmploymentRows(workbook.getSheet(SHEET_EMPLOYMENT), errors));
    }

    private List<OrgRow> readOrgRows(Sheet sheet) {
        List<OrgRow> rows = new ArrayList<>();
        for (int index = 1; index <= lastRow(sheet); index++) {
            Row row = sheet.getRow(index);
            if (blankRow(row)) {
                continue;
            }
            String typeRaw = text(row.getCell(2));
            rows.add(new OrgRow(index + 1, text(row.getCell(0)), text(row.getCell(1)), typeRaw,
                    orgType(typeRaw), text(row.getCell(3)), intValue(row.getCell(4)), text(row.getCell(5))));
        }
        return rows;
    }

    private List<PositionRow> readPositionRows(Sheet sheet) {
        List<PositionRow> rows = new ArrayList<>();
        for (int index = 1; index <= lastRow(sheet); index++) {
            Row row = sheet.getRow(index);
            if (blankRow(row)) {
                continue;
            }
            rows.add(new PositionRow(index + 1, text(row.getCell(0)), text(row.getCell(1)),
                    text(row.getCell(2)), intValue(row.getCell(3))));
        }
        return rows;
    }

    private List<PersonRow> readPersonRows(Sheet sheet) {
        List<PersonRow> rows = new ArrayList<>();
        for (int index = 1; index <= lastRow(sheet); index++) {
            Row row = sheet.getRow(index);
            if (blankRow(row)) {
                continue;
            }
            String typeRaw = text(row.getCell(2));
            rows.add(new PersonRow(index + 1, text(row.getCell(0)), text(row.getCell(1)), typeRaw,
                    personType(typeRaw), text(row.getCell(3)), text(row.getCell(4))));
        }
        return rows;
    }

    private List<EmploymentRow> readEmploymentRows(Sheet sheet, List<ImportError> errors) {
        List<EmploymentRow> rows = new ArrayList<>();
        for (int index = 1; index <= lastRow(sheet); index++) {
            Row row = sheet.getRow(index);
            if (blankRow(row)) {
                continue;
            }
            int line = index + 1;
            rows.add(new EmploymentRow(line, text(row.getCell(0)), text(row.getCell(1)), text(row.getCell(2)),
                    booleanValue(row.getCell(3)),
                    dateValue(row.getCell(4), SHEET_EMPLOYMENT, line, errors),
                    dateValue(row.getCell(5), SHEET_EMPLOYMENT, line, errors)));
        }
        return rows;
    }

    // ---------------------------------------------------------------- 校验

    /** 全量校验；返回解析出的组织单元深度（供落库排序复用）。 */
    private Map<String, Integer> validate(SheetContext context, List<ImportError> errors) {
        Map<String, OrgUnit> dbOrgUnits = new HashMap<>();
        orgUnits.findAll().forEach(unit -> dbOrgUnits.put(unit.code(), unit));
        Map<String, Person> dbPersons = new HashMap<>();
        persons.findAll().forEach(person -> dbPersons.put(person.empNo(), person));
        Map<String, Position> dbPositions = new HashMap<>();

        Set<String> fileOrgCodes = new HashSet<>();
        context.orgRows().forEach(row -> fileOrgCodes.add(row.code()));
        Set<String> fileEmpNos = new HashSet<>();
        context.personRows().forEach(row -> fileEmpNos.add(row.empNo()));
        Set<String> filePositionCodes = new HashSet<>();
        context.positionRows().forEach(row -> filePositionCodes.add(row.code()));

        Map<String, Integer> depth = resolveOrgDepth(context.orgRows(), dbOrgUnits, errors);
        validateOrgRows(context.orgRows(), fileOrgCodes, dbPersons, fileEmpNos, depth, errors);
        validatePositionRows(context.positionRows(), dbOrgUnits, fileOrgCodes, errors);
        validatePersonRows(context.personRows(), dbPersons, errors);
        validateEmploymentRows(context.employmentRows(), dbOrgUnits, dbPersons, fileOrgCodes, fileEmpNos,
                dbPositions, filePositionCodes, errors);
        return depth;
    }

    /** 解析组织单元深度：父编码取文件内优先、其次库内；缺父与成环均报错且不入选。 */
    private Map<String, Integer> resolveOrgDepth(List<OrgRow> rows, Map<String, OrgUnit> dbOrgUnits,
                                                 List<ImportError> errors) {
        Map<String, String> parentOf = new HashMap<>();
        Map<String, Integer> fileRow = new HashMap<>();
        for (OrgRow row : rows) {
            if (row.code() == null) {
                continue;
            }
            parentOf.put(row.code(), blank(row.parentCode()) ? null : row.parentCode());
            fileRow.put(row.code(), row.row());
        }
        Map<String, Integer> depth = new HashMap<>();
        Map<String, Integer> unresolved = new HashMap<>();
        for (Map.Entry<String, String> entry : parentOf.entrySet()) {
            Integer value = resolveDepth(entry.getKey(), parentOf, dbOrgUnits, depth,
                    new HashSet<>(), unresolved);
            if (value == null) {
                String parent = entry.getValue();
                String reason = parent == null ? "编码必填"
                        : parentOf.containsKey(parent) ? "组织单元层级存在环：" + parent
                        : "上级编码不存在：" + parent;
                errors.add(new ImportError(SHEET_ORG_UNIT, fileRow.getOrDefault(entry.getKey(), 0), reason));
            }
        }
        return depth;
    }

    private Integer resolveDepth(String code, Map<String, String> parentOf, Map<String, OrgUnit> dbOrgUnits,
                                 Map<String, Integer> memo, Set<String> visiting, Map<String, Integer> unresolved) {
        if (memo.containsKey(code) || unresolved.containsKey(code)) {
            return memo.get(code);
        }
        if (!visiting.add(code)) {
            return null;
        }
        String parent = parentOf.get(code);
        Integer value;
        if (parent == null) {
            value = 1;
        } else if (dbOrgUnits.containsKey(parent)) {
            value = dbOrgUnits.get(parent).depth() + 1;
        } else if (parentOf.containsKey(parent)) {
            Integer parentDepth = resolveDepth(parent, parentOf, dbOrgUnits, memo, visiting, unresolved);
            value = parentDepth == null ? null : parentDepth + 1;
        } else {
            value = null;
        }
        visiting.remove(code);
        if (value == null) {
            unresolved.put(code, 0);
        } else {
            memo.put(code, value);
        }
        return value;
    }

    private void validateOrgRows(List<OrgRow> rows, Set<String> fileOrgCodes, Map<String, Person> dbPersons,
                                 Set<String> fileEmpNos, Map<String, Integer> depth, List<ImportError> errors) {
        Set<String> seen = new HashSet<>();
        for (OrgRow row : rows) {
            if (blank(row.code())) {
                errors.add(new ImportError(SHEET_ORG_UNIT, row.row(), "编码必填"));
            } else if (!seen.add(row.code())) {
                errors.add(new ImportError(SHEET_ORG_UNIT, row.row(), "编码在文件内重复：" + row.code()));
            }
            if (blank(row.name())) {
                errors.add(new ImportError(SHEET_ORG_UNIT, row.row(), "名称必填"));
            }
            if (blank(row.typeRaw())) {
                errors.add(new ImportError(SHEET_ORG_UNIT, row.row(), "类型必填（GROUP / COMPANY / DEPARTMENT）"));
            } else if (row.type() == null) {
                errors.add(new ImportError(SHEET_ORG_UNIT, row.row(), "类型不合法：" + row.typeRaw()));
            }
            if (!blank(row.leaderEmpNo()) && !dbPersons.containsKey(row.leaderEmpNo())
                    && !fileEmpNos.contains(row.leaderEmpNo())) {
                errors.add(new ImportError(SHEET_ORG_UNIT, row.row(), "负责人工号不存在：" + row.leaderEmpNo()));
            }
            Integer value = row.code() == null ? null : depth.get(row.code());
            if (value != null && value > treePolicy.maxDepth()) {
                errors.add(new ImportError(SHEET_ORG_UNIT, row.row(),
                        "树深超限：depth=" + value + "，上限 " + treePolicy.maxDepth()));
            }
        }
    }

    private void validatePositionRows(List<PositionRow> rows, Map<String, OrgUnit> dbOrgUnits,
                                      Set<String> fileOrgCodes, List<ImportError> errors) {
        Set<String> seen = new HashSet<>();
        for (PositionRow row : rows) {
            if (blank(row.code())) {
                errors.add(new ImportError(SHEET_POSITION, row.row(), "编码必填"));
            } else if (!seen.add(row.code())) {
                errors.add(new ImportError(SHEET_POSITION, row.row(), "编码在文件内重复：" + row.code()));
            }
            if (blank(row.name())) {
                errors.add(new ImportError(SHEET_POSITION, row.row(), "名称必填"));
            }
            if (!blank(row.orgCode()) && !dbOrgUnits.containsKey(row.orgCode())
                    && !fileOrgCodes.contains(row.orgCode())) {
                errors.add(new ImportError(SHEET_POSITION, row.row(), "组织单元编码不存在：" + row.orgCode()));
            }
        }
    }

    private void validatePersonRows(List<PersonRow> rows, Map<String, Person> dbPersons, List<ImportError> errors) {
        Set<String> empNos = new HashSet<>();
        Set<String> mobileHashes = new HashSet<>();
        Set<String> emailHashes = new HashSet<>();
        for (PersonRow row : rows) {
            if (blank(row.empNo())) {
                errors.add(new ImportError(SHEET_PERSON, row.row(), "工号必填"));
            } else if (!empNos.add(row.empNo())) {
                errors.add(new ImportError(SHEET_PERSON, row.row(), "工号在文件内重复：" + row.empNo()));
            } else if (persons.existsByEmpNo(row.empNo()) && !dbPersons.containsKey(row.empNo())) {
                errors.add(new ImportError(SHEET_PERSON, row.row(), "工号已存在：" + row.empNo()));
            }
            if (blank(row.name())) {
                errors.add(new ImportError(SHEET_PERSON, row.row(), "姓名必填"));
            }
            if (blank(row.typeRaw())) {
                errors.add(new ImportError(SHEET_PERSON, row.row(),
                        "类型必填（EMPLOYEE / OUTSOURCED / DIGITAL_EMPLOYEE）"));
            } else if (row.type() == null) {
                errors.add(new ImportError(SHEET_PERSON, row.row(), "类型不合法：" + row.typeRaw()));
            }
            Person existing = dbPersons.get(row.empNo());
            String excludeId = existing == null ? null : existing.id();
            String mobileHash = cipher.hash(row.mobile());
            if (mobileHash != null) {
                if (!mobileHashes.add(mobileHash)) {
                    errors.add(new ImportError(SHEET_PERSON, row.row(), "手机号在文件内重复"));
                } else if (persons.existsByMobileHash(mobileHash, excludeId)) {
                    errors.add(new ImportError(SHEET_PERSON, row.row(), "手机号已被占用"));
                }
            }
            String emailHash = cipher.hash(row.email());
            if (emailHash != null) {
                if (!emailHashes.add(emailHash)) {
                    errors.add(new ImportError(SHEET_PERSON, row.row(), "邮箱在文件内重复"));
                } else if (persons.existsByEmailHash(emailHash, excludeId)) {
                    errors.add(new ImportError(SHEET_PERSON, row.row(), "邮箱已被占用"));
                }
            }
        }
    }

    private void validateEmploymentRows(List<EmploymentRow> rows, Map<String, OrgUnit> dbOrgUnits,
                                        Map<String, Person> dbPersons, Set<String> fileOrgCodes,
                                        Set<String> fileEmpNos, Map<String, Position> dbPositions,
                                        Set<String> filePositionCodes, List<ImportError> errors) {
        Set<String> primaryEmpNos = new HashSet<>();
        for (EmploymentRow row : rows) {
            if (blank(row.empNo()) || (!dbPersons.containsKey(row.empNo()) && !fileEmpNos.contains(row.empNo()))) {
                errors.add(new ImportError(SHEET_EMPLOYMENT, row.row(), "工号不存在：" + row.empNo()));
            }
            if (blank(row.orgCode()) || (!dbOrgUnits.containsKey(row.orgCode())
                    && !fileOrgCodes.contains(row.orgCode()))) {
                errors.add(new ImportError(SHEET_EMPLOYMENT, row.row(), "组织单元编码不存在：" + row.orgCode()));
            }
            if (!blank(row.positionCode()) && !filePositionCodes.contains(row.positionCode())
                    && positionLookup(dbPositions, row.positionCode()) == null) {
                errors.add(new ImportError(SHEET_EMPLOYMENT, row.row(), "岗位编码不存在：" + row.positionCode()));
            }
            if (row.startDate() == null) {
                errors.add(new ImportError(SHEET_EMPLOYMENT, row.row(), "开始日期必填"));
            } else if (row.endDate() != null && row.endDate().isBefore(row.startDate())) {
                errors.add(new ImportError(SHEET_EMPLOYMENT, row.row(), "结束日期早于开始日期"));
            }
            if (row.primary() && row.endDate() == null && !blank(row.empNo()) && !primaryEmpNos.add(row.empNo())) {
                errors.add(new ImportError(SHEET_EMPLOYMENT, row.row(),
                        "同一人员存在多条未结束主任职：" + row.empNo()));
            }
        }
    }

    private Position positionLookup(Map<String, Position> cache, String code) {
        if (cache.containsKey(code)) {
            return cache.get(code);
        }
        Position position = positions.findByCode(code).orElse(null);
        cache.put(code, position);
        return position;
    }

    // ---------------------------------------------------------------- 落库

    private void applyPersons(List<PersonRow> rows, Counts counts) {
        Map<String, Person> byEmpNo = new HashMap<>();
        persons.findAll().forEach(person -> byEmpNo.put(person.empNo(), person));
        forEachBatch(rows, row -> {
            Person existing = byEmpNo.get(row.empNo());
            String mobileCipher = cipher.encrypt(row.mobile());
            String emailCipher = cipher.encrypt(row.email());
            String mobileHash = cipher.hash(row.mobile());
            String emailHash = cipher.hash(row.email());
            if (existing == null) {
                Person created = new Person(Ids.newId(OrgIdPrefix.PERSON), row.empNo(), row.name(), row.type(),
                        PersonStatus.ACTIVE, mobileCipher, emailCipher, mobileHash, emailHash,
                        OrgSource.LOCAL, null, 0L);
                persons.insert(created);
                byEmpNo.put(row.empNo(), created);
                counts.created++;
                return;
            }
            boolean same = Objects.equals(existing.name(), row.name()) && existing.type() == row.type()
                    && Objects.equals(existing.mobileHash(), mobileHash)
                    && Objects.equals(existing.emailHash(), emailHash);
            if (same) {
                counts.skipped++;
                return;
            }
            Person updated = new Person(existing.id(), existing.empNo(), row.name(), row.type(), existing.status(),
                    mobileCipher, emailCipher, mobileHash, emailHash, existing.source(), existing.externalId(),
                    existing.version() + 1);
            persons.update(updated);
            byEmpNo.put(row.empNo(), updated);
            counts.updated++;
        });
    }

    private Map<String, String> applyOrgUnits(SheetContext context, Counts counts) {
        Map<String, OrgUnit> byCode = new HashMap<>();
        orgUnits.findAll().forEach(unit -> byCode.put(unit.code(), unit));
        Map<String, String> personIdByEmpNo = new HashMap<>();
        persons.findAll().forEach(person -> personIdByEmpNo.put(person.empNo(), person.id()));

        Map<String, OrgUnit> working = new HashMap<>(byCode);
        List<OrgRow> ordered = new ArrayList<>(context.orgRows());
        Map<String, Integer> depth = context.orgDepth();
        ordered.sort((left, right) -> Integer.compare(
                depth.getOrDefault(left.code(), treePolicy.maxDepth()),
                depth.getOrDefault(right.code(), treePolicy.maxDepth())));

        forEachBatch(ordered, row -> applyOrgUnit(row, byCode, working, personIdByEmpNo, counts));
        Map<String, String> ids = new HashMap<>();
        working.forEach((code, unit) -> ids.put(code, unit.id()));
        return ids;
    }

    private void applyOrgUnit(OrgRow row, Map<String, OrgUnit> byCode, Map<String, OrgUnit> working,
                              Map<String, String> personIdByEmpNo, Counts counts) {
        OrgUnit existing = byCode.get(row.code());
        OrgUnit parent = blank(row.parentCode()) ? null : working.get(row.parentCode());
        String id = existing == null ? Ids.newId(OrgIdPrefix.ORG_UNIT) : existing.id();
        String path = parent == null ? treePolicy.rootPath(id) : treePolicy.childPath(parent.path(), id);
        int depth = parent == null ? 1 : parent.depth() + 1;
        String parentId = parent == null ? null : parent.id();
        String leaderPersonId = blank(row.leaderEmpNo()) ? null : personIdByEmpNo.get(row.leaderEmpNo());

        if (existing == null) {
            OrgUnit created = new OrgUnit(id, row.code(), row.name(), row.type(), parentId, path, depth,
                    row.sortNo(), leaderPersonId, null, OrgStatus.ACTIVE, OrgSource.LOCAL, null, 0L);
            orgUnits.insert(created);
            working.put(row.code(), created);
            counts.created++;
            return;
        }
        boolean moved = !Objects.equals(parentId, existing.parentId());
        boolean same = !moved && Objects.equals(existing.name(), row.name()) && existing.type() == row.type()
                && existing.sortNo() == row.sortNo()
                && Objects.equals(existing.leaderPersonId(), leaderPersonId);
        if (same) {
            counts.skipped++;
            return;
        }
        OrgUnit updated = new OrgUnit(id, row.code(), row.name(), row.type(), parentId, path, depth,
                row.sortNo(), leaderPersonId, existing.effectiveAt(), existing.status(),
                existing.source(), existing.externalId(), existing.version() + 1);
        orgUnits.update(updated);
        if (moved) {
            orgUnits.rewriteSubtree(existing.path() + OrgTreePolicy.SEPARATOR,
                    path + OrgTreePolicy.SEPARATOR, depth - existing.depth());
        }
        working.put(row.code(), updated);
        counts.updated++;
    }

    private Map<String, String> applyPositions(List<PositionRow> rows, Counts counts) {
        Map<String, String> orgUnitIds = new HashMap<>();
        orgUnits.findAll().forEach(unit -> orgUnitIds.put(unit.code(), unit.id()));
        Map<String, String> ids = new HashMap<>();
        forEachBatch(rows, row -> {
            String orgUnitId = blank(row.orgCode()) ? null : orgUnitIds.get(row.orgCode());
            Position existing = positions.findByCode(row.code()).orElse(null);
            if (existing == null) {
                Position created = new Position(Ids.newId(OrgIdPrefix.POSITION), row.code(), row.name(), orgUnitId,
                        OrgStatus.ACTIVE, row.sortNo(), OrgSource.LOCAL, null);
                positions.insert(created);
                ids.put(row.code(), created.id());
                counts.created++;
                return;
            }
            ids.put(row.code(), existing.id());
            boolean same = Objects.equals(existing.name(), row.name())
                    && Objects.equals(existing.orgUnitId(), orgUnitId) && existing.sortNo() == row.sortNo();
            if (same) {
                counts.skipped++;
                return;
            }
            positions.update(new Position(existing.id(), row.code(), row.name(), orgUnitId, existing.status(),
                    row.sortNo(), existing.source(), existing.externalId()));
            counts.updated++;
        });
        return ids;
    }

    private void applyEmployments(List<EmploymentRow> rows, Map<String, String> orgUnitIds,
                                  Map<String, String> positionIds, Counts counts) {
        Map<String, String> personIds = new HashMap<>();
        persons.findAll().forEach(person -> personIds.put(person.empNo(), person.id()));
        forEachBatch(rows, row -> {
            String personId = personIds.get(row.empNo());
            String orgUnitId = orgUnitIds.get(row.orgCode());
            String positionId = blank(row.positionCode()) ? null : positionIds.get(row.positionCode());
            if (personId == null || orgUnitId == null) {
                return;
            }
            Optional<Employment> match = employments.byPerson(personId).stream()
                    .filter(item -> Objects.equals(item.orgUnitId(), orgUnitId)
                            && Objects.equals(item.startDate(), row.startDate()))
                    .findFirst();
            if (match.isEmpty()) {
                if (row.primary() && row.endDate() == null) {
                    closeSwitchedPrimary(personId, null, row.startDate());
                }
                employments.insert(new Employment(Ids.newId(OrgIdPrefix.EMPLOYMENT), personId, orgUnitId,
                        positionId, row.primary(), row.startDate(), row.endDate()));
                counts.created++;
                return;
            }
            Employment existing = match.get();
            boolean same = existing.primary() == row.primary()
                    && Objects.equals(existing.positionId(), positionId)
                    && Objects.equals(existing.endDate(), row.endDate());
            if (same) {
                counts.skipped++;
                return;
            }
            if (row.primary() && row.endDate() == null) {
                closeSwitchedPrimary(personId, existing.id(), row.startDate());
            }
            employments.update(new Employment(existing.id(), personId, orgUnitId, positionId, row.primary(),
                    row.startDate(), row.endDate()));
            counts.updated++;
        });
    }

    private void closeSwitchedPrimary(String personId, String keepEmploymentId, LocalDate endDate) {
        for (String id : employmentRule.primarySwitchPlan(keepEmploymentId, employments.byPerson(personId))) {
            employments.close(id, endDate);
        }
    }

    private <T> void forEachBatch(List<T> rows, Consumer<T> action) {
        int batchSize = Math.max(1, properties.getBatchSize());
        for (int from = 0; from < rows.size(); from += batchSize) {
            int to = Math.min(from + batchSize, rows.size());
            for (int index = from; index < to; index++) {
                action.accept(rows.get(index));
            }
        }
    }

    // ---------------------------------------------------------------- 单元格工具

    private static int lastRow(Sheet sheet) {
        return sheet == null ? 0 : sheet.getLastRowNum();
    }

    private static boolean blankRow(Row row) {
        if (row == null) {
            return true;
        }
        for (int index = row.getFirstCellNum(); index < row.getLastCellNum(); index++) {
            if (!blank(text(row.getCell(index)))) {
                return false;
            }
        }
        return true;
    }

    private static String text(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        return switch (type) {
            case STRING -> trimToNull(cell.getStringCellValue());
            case NUMERIC -> {
                double value = cell.getNumericCellValue();
                yield value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private static int intValue(Cell cell) {
        String raw = text(cell);
        if (raw == null) {
            return 0;
        }
        try {
            return (int) Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static boolean booleanValue(Cell cell) {
        String raw = text(cell);
        if (raw == null) {
            return false;
        }
        return switch (raw.toLowerCase()) {
            case "是", "y", "yes", "true", "1" -> true;
            default -> false;
        };
    }

    private static LocalDate dateValue(Cell cell, String sheet, int line, List<ImportError> errors) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String raw = text(cell);
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException ex) {
            errors.add(new ImportError(sheet, line, "日期格式不正确（应为 yyyy-MM-dd）：" + raw));
            return null;
        }
    }

    private static OrgUnitType orgType(String raw) {
        return enumOf(raw, OrgUnitType.class);
    }

    private static PersonType personType(String raw) {
        return enumOf(raw, PersonType.class);
    }

    private static <E extends Enum<E>> E enumOf(String raw, Class<E> type) {
        if (blank(raw)) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 一个导入文件的全量解析结果。
     *
     * @param orgRows        组织单元行
     * @param positionRows   岗位行
     * @param personRows     人员行
     * @param employmentRows 任职行
     * @param orgDepth       组织单元编码 → 解析出的深度（校验与落库排序共用）
     */
    private record SheetContext(List<OrgRow> orgRows, List<PositionRow> positionRows, List<PersonRow> personRows,
                                List<EmploymentRow> employmentRows, Map<String, Integer> orgDepth) {

        private SheetContext(List<OrgRow> orgRows, List<PositionRow> positionRows, List<PersonRow> personRows,
                             List<EmploymentRow> employmentRows) {
            this(orgRows, positionRows, personRows, employmentRows, Map.of());
        }

        private int totalRows() {
            return orgRows.size() + positionRows.size() + personRows.size() + employmentRows.size();
        }

        private SheetContext withOrgDepth(Map<String, Integer> depth) {
            return new SheetContext(orgRows, positionRows, personRows, employmentRows, depth);
        }
    }

    /** 导入计数。 */
    private static final class Counts {
        private int created;
        private int updated;
        private int skipped;
    }

    private record OrgRow(int row, String code, String name, String typeRaw, OrgUnitType type, String parentCode,
                          int sortNo, String leaderEmpNo) {
    }

    private record PositionRow(int row, String code, String name, String orgCode, int sortNo) {
    }

    private record PersonRow(int row, String empNo, String name, String typeRaw, PersonType type,
                             String mobile, String email) {
    }

    private record EmploymentRow(int row, String empNo, String orgCode, String positionCode, boolean primary,
                                 LocalDate startDate, LocalDate endDate) {
    }
}
