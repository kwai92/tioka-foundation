package com.foundation.org.application;

import com.foundation.org.api.dto.ImportResult;
import com.foundation.org.api.dto.OrgStatus;
import com.foundation.org.api.dto.OrgUnitType;
import com.foundation.org.api.dto.PersonStatus;
import com.foundation.org.api.dto.PersonType;
import com.foundation.org.config.OrgImportProperties;
import com.foundation.org.domain.Employment;
import com.foundation.org.domain.EmploymentRule;
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
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** F-03 BAS-02 §4.1：Excel 导入整表校验、按业务键幂等、深度排序落库。 */
class OrgImportServiceTest {

    private static final String SHEET_ORG_UNIT = "组织单元";
    private static final String SHEET_POSITION = "岗位";
    private static final String SHEET_PERSON = "人员";
    private static final String SHEET_EMPLOYMENT = "任职";

    private final InMemoryOrgUnitRepository orgUnits = new InMemoryOrgUnitRepository();
    private final InMemoryPositionRepository positions = new InMemoryPositionRepository();
    private final InMemoryPersonRepository persons = new InMemoryPersonRepository();
    private final InMemoryEmploymentRepository employments = new InMemoryEmploymentRepository();

    private OrgImportService service() {
        return new OrgImportService(orgUnits, positions, persons, employments,
                new PersonCipher("unit-test-master-key"), new OrgTreePolicy(10), new EmploymentRule(),
                mock(OrgChangeLogWriter.class), new OrgImportProperties());
    }

    @Test
    void importPersistsOrgPositionPersonEmploymentAndOrdersByDepth() {
        ImportResult result = service().importWorkbook(workbook(false));

        assertThat(result.errors()).isEmpty();
        assertThat(result.created()).isEqualTo(6);
        assertThat(result.updated()).isZero();
        assertThat(result.skipped()).isZero();

        // 组织单元按解析出的深度排序落库：即使文件中子节点在前，深度仍正确
        OrgUnit root = orgUnits.byCode("ORG_ROOT");
        OrgUnit dept = orgUnits.byCode("ORG_DEPT");
        OrgUnit team = orgUnits.byCode("ORG_TEAM");
        assertThat(root.depth()).isEqualTo(1);
        assertThat(root.parentId()).isNull();
        assertThat(dept.depth()).isEqualTo(2);
        assertThat(dept.parentId()).isEqualTo(root.id());
        assertThat(team.depth()).isEqualTo(3);
        assertThat(team.parentId()).isEqualTo(dept.id());
        assertThat(team.path()).isEqualTo(dept.path() + "/" + team.id());

        assertThat(persons.byEmpNo("E1001").name()).isEqualTo("张三");
        assertThat(positions.byCode("POS_DEV").name()).isEqualTo("开发工程师");
        Employment employment = employments.all().get(0);
        assertThat(employment.primary()).isTrue();
        assertThat(employment.personId()).isEqualTo(persons.byEmpNo("E1001").id());
        assertThat(employment.orgUnitId()).isEqualTo(team.id());
    }

    @Test
    void reimportIsIdempotentByBusinessKey() {
        OrgImportService service = service();
        service.importWorkbook(workbook(false));

        ImportResult second = service.importWorkbook(workbook(false));

        assertThat(second.errors()).isEmpty();
        assertThat(second.created()).isZero();
        assertThat(second.updated()).isZero();
        assertThat(second.skipped()).isEqualTo(6);
        assertThat(orgUnits.size()).isEqualTo(3);
        assertThat(persons.size()).isEqualTo(1);
        assertThat(employments.size()).isEqualTo(1);
    }

    @Test
    void unknownParentCodeRejectsWholeTableWithoutPersisting() {
        ImportResult result = service().importWorkbook(workbook(true));

        assertThat(result.rejected()).isTrue();
        assertThat(result.created()).isZero();
        assertThat(result.errors()).anyMatch(error -> error.message().contains("上级编码不存在"));
        assertThat(orgUnits.size()).isZero();
        assertThat(persons.size()).isZero();
    }

    @Test
    void resultIsRetrievableByTaskId() {
        OrgImportService service = service();
        ImportResult result = service.importWorkbook(workbook(false));

        assertThat(service.result(result.taskId())).contains(result);
        assertThat(service.result("imp_missing")).isEmpty();
    }

    // ---------------------------------------------------------------- 造数

    private static ByteArrayInputStream workbook(boolean brokenParent) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet org = workbook.createSheet(SHEET_ORG_UNIT);
            header(org, "编码", "名称", "类型", "上级编码", "排序", "负责人工号");
            // 故意把子节点排在前，验证落库按深度而非文件顺序
            data(org, 1, "ORG_TEAM", "前端组", "DEPARTMENT", "ORG_DEPT", "1", "");
            data(org, 2, "ORG_DEPT", "研发部", "DEPARTMENT",
                    brokenParent ? "ORG_MISSING" : "ORG_ROOT", "10", "");
            data(org, 3, "ORG_ROOT", "总部", "COMPANY", "", "1", "");

            Sheet position = workbook.createSheet(SHEET_POSITION);
            header(position, "编码", "名称", "组织单元编码", "排序");
            data(position, 1, "POS_DEV", "开发工程师", "ORG_TEAM", "1");

            Sheet person = workbook.createSheet(SHEET_PERSON);
            header(person, "工号", "姓名", "类型", "手机", "邮箱");
            data(person, 1, "E1001", "张三", "EMPLOYEE", "13800138000", "zhangsan@example.com");

            Sheet employment = workbook.createSheet(SHEET_EMPLOYMENT);
            header(employment, "工号", "组织单元编码", "岗位编码", "是否主任职", "开始日期", "结束日期");
            data(employment, 1, "E1001", "ORG_TEAM", "POS_DEV", "是", "2026-01-01", "");

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            workbook.write(buffer);
            return new ByteArrayInputStream(buffer.toByteArray());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void header(Sheet sheet, String... titles) {
        Row row = sheet.createRow(0);
        for (int index = 0; index < titles.length; index++) {
            row.createCell(index).setCellValue(titles[index]);
        }
    }

    private static void data(Sheet sheet, int rowIndex, String... values) {
        Row row = sheet.createRow(rowIndex);
        for (int index = 0; index < values.length; index++) {
            row.createCell(index).setCellValue(values[index]);
        }
    }

    // ---------------------------------------------------------------- 内存仓储

    private static final class InMemoryOrgUnitRepository implements OrgUnitRepository {

        private final Map<String, OrgUnit> byId = new LinkedHashMap<>();

        OrgUnit byCode(String code) {
            return findByCode(code).orElseThrow();
        }

        int size() {
            return byId.size();
        }

        @Override
        public Optional<OrgUnit> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<OrgUnit> findByCode(String code) {
            return byId.values().stream().filter(unit -> unit.code().equals(code)).findFirst();
        }

        @Override
        public Optional<OrgUnit> findBySourceExternalId(String source, String externalId) {
            return Optional.empty();
        }

        @Override
        public boolean existsByCode(String code) {
            return findByCode(code).isPresent();
        }

        @Override
        public List<OrgUnit> childrenOf(String parentId) {
            return byId.values().stream().filter(unit -> Objects.equals(unit.parentId(), parentId)).toList();
        }

        @Override
        public List<OrgUnit> subtreeOf(String orgUnitId) {
            OrgUnit root = byId.get(orgUnitId);
            if (root == null) {
                return List.of();
            }
            return byId.values().stream()
                    .filter(unit -> unit.path().equals(root.path()) || unit.path().startsWith(root.path() + "/"))
                    .toList();
        }

        @Override
        public List<OrgUnit> findAll() {
            return new ArrayList<>(byId.values());
        }

        @Override
        public Set<String> descendantIds(String orgUnitId) {
            return subtreeOf(orgUnitId).stream().map(OrgUnit::id).collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public List<OrgUnit> search(String q, OrgUnitType type, OrgStatus status, int page, int size) {
            return findAll();
        }

        @Override
        public long count(String q, OrgUnitType type, OrgStatus status) {
            return byId.size();
        }

        @Override
        public void insert(OrgUnit orgUnit) {
            byId.put(orgUnit.id(), orgUnit);
        }

        @Override
        public void update(OrgUnit orgUnit) {
            byId.put(orgUnit.id(), orgUnit);
        }

        @Override
        public int rewriteSubtree(String oldPrefix, String newPrefix, int depthDelta) {
            int affected = 0;
            for (OrgUnit unit : findAll()) {
                if (unit.path().startsWith(oldPrefix)) {
                    String path = newPrefix + unit.path().substring(oldPrefix.length());
                    byId.put(unit.id(), new OrgUnit(unit.id(), unit.code(), unit.name(), unit.type(),
                            unit.parentId(), path, unit.depth() + depthDelta, unit.sortNo(), unit.leaderPersonId(),
                            unit.effectiveAt(), unit.status(), unit.source(), unit.externalId(), unit.version()));
                    affected++;
                }
            }
            return affected;
        }
    }

    private static final class InMemoryPositionRepository implements PositionRepository {

        private final Map<String, Position> byId = new LinkedHashMap<>();

        Position byCode(String code) {
            return findByCode(code).orElseThrow();
        }

        @Override
        public Optional<Position> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Position> findByCode(String code) {
            return byId.values().stream().filter(position -> position.code().equals(code)).findFirst();
        }

        @Override
        public Optional<Position> findBySourceExternalId(String source, String externalId) {
            return Optional.empty();
        }

        @Override
        public boolean existsByCode(String code) {
            return findByCode(code).isPresent();
        }

        @Override
        public List<Position> list(int page, int size) {
            return new ArrayList<>(byId.values());
        }

        @Override
        public long count() {
            return byId.size();
        }

        @Override
        public List<Position> findByOrgUnit(String orgUnitId) {
            return byId.values().stream().filter(position -> Objects.equals(position.orgUnitId(), orgUnitId)).toList();
        }

        @Override
        public Map<String, Long> countByOrgUnit(Collection<String> orgUnitIds) {
            return Map.of();
        }

        @Override
        public void insert(Position position) {
            byId.put(position.id(), position);
        }

        @Override
        public void update(Position position) {
            byId.put(position.id(), position);
        }

        @Override
        public void deleteById(String id) {
            byId.remove(id);
        }
    }

    private static final class InMemoryPersonRepository implements PersonRepository {

        private final Map<String, Person> byId = new LinkedHashMap<>();

        Person byEmpNo(String empNo) {
            return findByEmpNo(empNo).orElseThrow();
        }

        int size() {
            return byId.size();
        }

        @Override
        public Optional<Person> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<Person> findByEmpNo(String empNo) {
            return byId.values().stream().filter(person -> person.empNo().equals(empNo)).findFirst();
        }

        @Override
        public Optional<Person> findByEmailHash(String hash) {
            return byId.values().stream().filter(person -> Objects.equals(person.emailHash(), hash)).findFirst();
        }

        @Override
        public Optional<Person> findBySourceExternalId(String source, String externalId) {
            return Optional.empty();
        }

        @Override
        public List<Person> findByIds(Collection<String> ids) {
            return byId.values().stream().filter(person -> ids.contains(person.id())).toList();
        }

        @Override
        public boolean existsByEmpNo(String empNo) {
            return findByEmpNo(empNo).isPresent();
        }

        @Override
        public boolean existsByMobileHash(String hash, String excludePersonId) {
            return byId.values().stream()
                    .filter(person -> !person.id().equals(excludePersonId))
                    .anyMatch(person -> Objects.equals(person.mobileHash(), hash));
        }

        @Override
        public boolean existsByEmailHash(String hash, String excludePersonId) {
            return byId.values().stream()
                    .filter(person -> !person.id().equals(excludePersonId))
                    .anyMatch(person -> Objects.equals(person.emailHash(), hash));
        }

        @Override
        public List<Person> search(String q, PersonType type, PersonStatus status,
                                   Collection<String> restrictPersonIds, int page, int size) {
            return findAll();
        }

        @Override
        public long count(String q, PersonType type, PersonStatus status, Collection<String> restrictPersonIds) {
            return byId.size();
        }

        @Override
        public List<Person> findAll() {
            return new ArrayList<>(byId.values());
        }

        @Override
        public void insert(Person person) {
            byId.put(person.id(), person);
        }

        @Override
        public void update(Person person) {
            byId.put(person.id(), person);
        }
    }

    private static final class InMemoryEmploymentRepository implements EmploymentRepository {

        private final Map<String, Employment> byId = new LinkedHashMap<>();

        List<Employment> all() {
            return new ArrayList<>(byId.values());
        }

        int size() {
            return byId.size();
        }

        @Override
        public Optional<Employment> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<Employment> byPerson(String personId) {
            return byId.values().stream()
                    .filter(employment -> employment.personId().equals(personId))
                    .sorted((left, right) -> left.startDate().compareTo(right.startDate()))
                    .toList();
        }

        @Override
        public List<Employment> openByPerson(String personId) {
            return byPerson(personId).stream().filter(Employment::open).toList();
        }

        @Override
        public Optional<Employment> openPrimary(String personId) {
            return openByPerson(personId).stream().filter(Employment::primary).findFirst();
        }

        @Override
        public List<Employment> openByOrgUnit(String orgUnitId) {
            return byId.values().stream()
                    .filter(Employment::open)
                    .filter(employment -> employment.orgUnitId().equals(orgUnitId))
                    .toList();
        }

        @Override
        public Set<String> openPersonIdsInOrgUnits(Collection<String> orgUnitIds) {
            return byId.values().stream()
                    .filter(Employment::open)
                    .filter(employment -> orgUnitIds.contains(employment.orgUnitId()))
                    .map(Employment::personId)
                    .collect(java.util.stream.Collectors.toSet());
        }

        @Override
        public long countOpenByOrgUnit(String orgUnitId) {
            return openByOrgUnit(orgUnitId).size();
        }

        @Override
        public void insert(Employment employment) {
            byId.put(employment.id(), employment);
        }

        @Override
        public void update(Employment employment) {
            byId.put(employment.id(), employment);
        }

        @Override
        public void close(String id, LocalDate endDate) {
            byId.computeIfPresent(id, (key, employment) -> employment.closed(endDate));
        }

        @Override
        public void deleteById(String id) {
            byId.remove(id);
        }
    }
}
