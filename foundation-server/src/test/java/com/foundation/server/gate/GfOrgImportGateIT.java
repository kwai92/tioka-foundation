package com.foundation.server.gate;

import com.foundation.org.api.dto.ImportResult;
import com.foundation.org.api.dto.PersonType;
import com.foundation.org.application.OrgImportService;
import com.foundation.org.domain.Employment;
import com.foundation.org.domain.OrgUnit;
import com.foundation.org.domain.Person;
import com.foundation.org.domain.Position;
import com.foundation.org.infrastructure.EmploymentRepository;
import com.foundation.org.infrastructure.OrgUnitRepository;
import com.foundation.org.infrastructure.PersonRepository;
import com.foundation.org.infrastructure.PositionRepository;
import com.foundation.org.infrastructure.person.PersonCipher;
import com.foundation.test.FoundationContainers;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 闸门 GF（F-00 §6.1，BAS-06 / W81）：组织 Excel 导入在**真实库**上的落库正确性与幂等性。
 *
 * <p>验收口径（F-00 §6.1）：导入 500 人组织后，组织树、岗位、兼职、数字员工任职正确；重复导入幂等。
 *
 * <p><b>与既有单测的分工</b>：{@code OrgImportServiceTest} 用 Mockito 内存假仓储，验的是
 * {@code OrgImportService} 的解析/校验/排序算法；本闸门跑完整 {@code FoundationApplication} + PostgreSQL
 * 容器，验的是**算法之下那层单测碰不到的东西**——MyBatis 映射与真表 DDL 是否吻合（列名/类型/约束）、
 * Flyway 迁移出的 schema 能否承载 500 人批量写入、真实事务边界下重复导入是否幂等、
 * 以及 {@code TIMESTAMPTZ} 等类型在真实读写路径上不炸。
 *
 * <p><b>刻意不调用 {@code DigitalEmployeeRule}</b>：核实结论是导入路径根本不引用该规则
 * （{@code OrgImportService} 无 owner 字段、模板 v1 人员 sheet 也无「负责人」列），
 * 故本闸门只断言数字员工**建档与主任职落库正确**，不断言 owner 约束——那是
 * Tioka 经接口维护数字员工时（BAS-03）的语义，不在导入链路上。此缺口已登记待负责人决策。
 *
 * <p>运行：{@code mvn -B verify -Pgate}（gate profile 由 surefire 以 {@code groups=gate} 执行 IT）。
 */
@Tag("gate")
@SpringBootTest
class GfOrgImportGateIT {

    /** 1 个集团根 + 20 个部门。 */
    private static final int DEPARTMENT_COUNT = 20;
    private static final int ORG_COUNT = DEPARTMENT_COUNT + 1;
    /** 每部门一个岗位。 */
    private static final int POSITION_COUNT = DEPARTMENT_COUNT;
    /** F-00 §6.1 要求的 500 人规模。 */
    private static final int PERSON_COUNT = 500;
    /** 其中 5 人为数字员工（PersonType.DIGITAL_EMPLOYEE）。 */
    private static final int DIGITAL_EMPLOYEE_COUNT = 5;
    /** 前 50 人额外挂一条兼职。 */
    private static final int SECONDARY_COUNT = 50;
    private static final int EMPLOYMENT_COUNT = PERSON_COUNT + SECONDARY_COUNT;
    /** created 口径 = 四类实体新增行数之和（applyXxx 各自 counts.created++）。 */
    private static final int TOTAL_ROWS = ORG_COUNT + POSITION_COUNT + PERSON_COUNT + EMPLOYMENT_COUNT;

    private static final String ROOT_CODE = "ORG_ROOT";
    private static final String LEADER_EMP_NO = "E0001";
    private static final String PRIMARY_START = "2026-01-01";
    private static final String SECONDARY_START = "2026-02-01";

    /** sheet 名与表头必须逐字对齐 OrgImportService 的模板 v1 约定（列序即解析序）。 */
    private static final String SHEET_ORG = "组织单元";
    private static final String SHEET_POSITION = "岗位";
    private static final String SHEET_PERSON = "人员";
    private static final String SHEET_EMPLOYMENT = "任职";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        FoundationContainers.register(registry);
    }

    @Autowired
    private OrgImportService imports;

    @Autowired
    private OrgUnitRepository orgUnits;

    @Autowired
    private PositionRepository positions;

    @Autowired
    private PersonRepository persons;

    @Autowired
    private EmploymentRepository employments;

    @Autowired
    private PersonCipher cipher;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanUp() {
        // 逆外键依赖顺序清理：子表先于父表，避免 TRUNCATE 触发 FK 违规。
        // fdn_external_identity（account 域）引用 fdn_person，必须一并清理。
        jdbc.execute("TRUNCATE fdn_employment, fdn_report_line, fdn_external_identity, "
                + "fdn_position, fdn_org_change_log, fdn_job_grade, fdn_org_unit, fdn_person");
    }

    @Test
    void imports500PersonOrgWithCorrectTreePositionsAndConcurrentPosts() {
        ImportResult result = imports.importWorkbook(workbook(null));

        assertThat(result.errors()).as("导入不应有任何行级错误").isEmpty();
        assertThat(result.rejected()).isFalse();
        assertThat(result.created()).isEqualTo(TOTAL_ROWS);
        assertThat(result.updated()).isZero();
        assertThat(result.skipped()).isZero();

        assertOrgTreePersisted();
        assertPositionsPersisted();
        assertPersonsPersisted();
        assertEmploymentsPersisted();
        assertDigitalEmployeesHavePrimaryEmployment();
    }

    @Test
    void reimportIsIdempotentOnRealDatabase() {
        imports.importWorkbook(workbook(null));

        ImportResult second = imports.importWorkbook(workbook(null));

        assertThat(second.errors()).isEmpty();
        assertThat(second.created()).as("重复导入不应再新增任何行").isZero();
        assertThat(second.updated()).as("内容未变不应触发更新").isZero();
        assertThat(second.skipped()).as("跳过数应等于数据行总数").isEqualTo(TOTAL_ROWS);

        // 真库行数不变，是「幂等」最硬的证据（内存仓储单测无法覆盖）
        assertThat(count("fdn_org_unit")).isEqualTo(ORG_COUNT);
        assertThat(count("fdn_position")).isEqualTo(POSITION_COUNT);
        assertThat(count("fdn_person")).isEqualTo(PERSON_COUNT);
        assertThat(count("fdn_employment")).isEqualTo(EMPLOYMENT_COUNT);
    }

    @Test
    void brokenParentCodeRejectsWholeTableOnRealDatabase() {
        // 整表拒绝必须在真库上验证：单测的内存仓储无法证明「未落库」在真实事务边界下同样成立
        ImportResult result = imports.importWorkbook(workbook("ORG_MISSING"));

        assertThat(result.rejected()).isTrue();
        assertThat(result.created()).isZero();
        assertThat(result.errors())
                .anyMatch(error -> error.message().contains("上级编码不存在"));

        assertThat(count("fdn_org_unit")).isZero();
        assertThat(count("fdn_person")).isZero();
        assertThat(count("fdn_position")).isZero();
        assertThat(count("fdn_employment")).isZero();
    }

    // ---------------------------------------------------------------- 断言

    /** 组织树：深度、父子链与物化路径按解析出的深度落库，与文件行序无关。 */
    private void assertOrgTreePersisted() {
        OrgUnit root = requireOrg(ROOT_CODE);
        assertThat(root.depth()).isEqualTo(1);
        assertThat(root.parentId()).isNull();

        // 负责人工号需被解析为 person_id（跨 sheet 引用，单测用内存仓储也能过，但真库才验证外键写入）
        assertThat(root.leaderPersonId())
                .as("根组织负责人应解析为 %s 的 person id", LEADER_EMP_NO)
                .isEqualTo(requirePerson(LEADER_EMP_NO).id());

        for (int index = 0; index < DEPARTMENT_COUNT; index++) {
            OrgUnit dept = requireOrg(deptCode(index));
            assertThat(dept.depth()).as("%s 深度", dept.code()).isEqualTo(2);
            assertThat(dept.parentId()).as("%s 父节点", dept.code()).isEqualTo(root.id());
            assertThat(dept.path()).as("%s 物化路径应挂在根路径下", dept.code())
                    .isEqualTo(root.path() + "/" + dept.id());
        }
    }

    /** 岗位：编码唯一、挂在对应部门下。 */
    private void assertPositionsPersisted() {
        assertThat(count("fdn_position")).isEqualTo(POSITION_COUNT);
        for (int index = 0; index < POSITION_COUNT; index++) {
            Optional<Position> position = positions.findByCode(positionCode(index));
            assertThat(position).as("岗位 %s 应存在", positionCode(index)).isPresent();
            assertThat(position.get().orgUnitId())
                    .as("岗位 %s 应挂在 %s 下", positionCode(index), deptCode(index))
                    .isEqualTo(requireOrg(deptCode(index)).id());
        }
    }

    /** 人员：500 人全部落库，类型分布正确，敏感字段以密文入库（不存明文）。 */
    private void assertPersonsPersisted() {
        assertThat(count("fdn_person")).isEqualTo(PERSON_COUNT);
        assertThat(persons.findByEmpNo(LEADER_EMP_NO)).isPresent();
        assertThat(persons.findByEmpNo(empNo(PERSON_COUNT))).as("最后一名人员应落库").isPresent();

        // 加密落库是 F-03 §7.1 的硬要求。不只断言「不等于明文」（弱断言：null/乱码也能过），
        // 而是验证真契约——密文带 v1: 信封、且能用注入的 PersonCipher 精确解密回原始明文。
        // 这一步还坐实了「写入密文的 cipher 与读取解密的 cipher 是同一个 bean、同一主密钥」，
        // 单测的内存假仓储无法覆盖这条真实加解密往返。
        String plainMobile = mobile(1);
        String storedCipher = jdbc.queryForObject(
                "SELECT mobile_cipher FROM fdn_person WHERE emp_no = ?", String.class, empNo(1));
        assertThat(storedCipher).as("手机号必须密文入库").isNotBlank().startsWith("v1:");
        assertThat(storedCipher).as("密文不得是明文").doesNotContain(plainMobile);
        assertThat(cipher.decrypt(storedCipher)).as("注入的 cipher 应能解回原始手机号")
                .isEqualTo(plainMobile);
    }

    /** 任职：每人恰一条未结束主任职，前 50 人另有兼职，任职指向的组织与岗位外键正确。 */
    private void assertEmploymentsPersisted() {
        assertThat(count("fdn_employment")).isEqualTo(EMPLOYMENT_COUNT);

        for (int seq = 1; seq <= PERSON_COUNT; seq++) {
            Person person = requirePerson(empNo(seq));
            List<Employment> all = employments.byPerson(person.id());

            int expectedTotal = seq <= SECONDARY_COUNT ? 2 : 1;
            assertThat(all).as("%s 的任职条数", person.empNo()).hasSize(expectedTotal);
            assertThat(employments.openPrimary(person.id()))
                    .as("%s 必须有且仅有一条未结束主任职", person.empNo())
                    .isPresent();

            int expectedDept = (seq - 1) % DEPARTMENT_COUNT;
            assertThat(employments.openPrimary(person.id()).get().orgUnitId())
                    .as("%s 的主任职部门", person.empNo())
                    .isEqualTo(requireOrg(deptCode(expectedDept)).id());

            if (seq <= SECONDARY_COUNT) {
                // 兼职必须与主任职落在不同部门，否则「兼职」这个语义在数据上不成立
                String secondaryOrg = requireOrg(deptCode((expectedDept + 1) % DEPARTMENT_COUNT)).id();
                assertThat(all).as("%s 应有跨部门兼职", person.empNo())
                        .anyMatch(item -> !item.primary() && item.orgUnitId().equals(secondaryOrg));
            }
        }
    }

    /** 数字员工：建档类型正确且必挂主任职（F-03 §3）。 */
    private void assertDigitalEmployeesHavePrimaryEmployment() {
        List<Person> digital = persons.findAll().stream()
                .filter(person -> person.type() == PersonType.DIGITAL_EMPLOYEE)
                .toList();
        assertThat(digital).as("数字员工人数").hasSize(DIGITAL_EMPLOYEE_COUNT);
        for (Person person : digital) {
            assertThat(employments.openPrimary(person.id()))
                    .as("数字员工 %s 必须挂主任职到其服务部门", person.empNo())
                    .isPresent();
        }
    }

    // ---------------------------------------------------------------- 造数

    /**
     * 构造 500 人规模的导入工作簿（模板 v1 列序）。
     *
     * @param brokenParentCode 非 null 时把某个部门的上级编码改成该值，用于验证整表拒绝
     */
    private static ByteArrayInputStream workbook(String brokenParentCode) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet org = workbook.createSheet(SHEET_ORG);
            header(org, "编码", "名称", "类型", "上级编码", "排序", "负责人工号");
            int orgRow = 1;
            // 刻意把部门写在根之前：验证落库按解析深度排序，而非文件行序
            for (int index = 0; index < DEPARTMENT_COUNT; index++) {
                String parent = (brokenParentCode != null && index == 0) ? brokenParentCode : ROOT_CODE;
                data(org, orgRow++, deptCode(index), "部门" + index, "DEPARTMENT", parent,
                        String.valueOf(index), "");
            }
            data(org, orgRow, ROOT_CODE, "集团总部", "GROUP", "", "1", LEADER_EMP_NO);

            Sheet position = workbook.createSheet(SHEET_POSITION);
            header(position, "编码", "名称", "组织单元编码", "排序");
            for (int index = 0; index < POSITION_COUNT; index++) {
                data(position, index + 1, positionCode(index), "岗位" + index, deptCode(index),
                        String.valueOf(index));
            }

            Sheet person = workbook.createSheet(SHEET_PERSON);
            header(person, "工号", "姓名", "类型", "手机", "邮箱");
            for (int seq = 1; seq <= PERSON_COUNT; seq++) {
                // 末 5 人为数字员工
                boolean digital = seq > PERSON_COUNT - DIGITAL_EMPLOYEE_COUNT;
                data(person, seq, empNo(seq), "人员" + seq,
                        digital ? "DIGITAL_EMPLOYEE" : "EMPLOYEE", mobile(seq), email(seq));
            }

            Sheet employment = workbook.createSheet(SHEET_EMPLOYMENT);
            header(employment, "工号", "组织单元编码", "岗位编码", "是否主任职", "开始日期", "结束日期");
            int employmentRow = 1;
            for (int seq = 1; seq <= PERSON_COUNT; seq++) {
                int dept = (seq - 1) % DEPARTMENT_COUNT;
                data(employment, employmentRow++, empNo(seq), deptCode(dept), positionCode(dept),
                        "是", PRIMARY_START, "");
            }
            // 前 50 人再挂一条跨部门兼职（不同部门 + 不同开始日期，避免与主任职被判定为同一行）
            for (int seq = 1; seq <= SECONDARY_COUNT; seq++) {
                int dept = ((seq - 1) % DEPARTMENT_COUNT + 1) % DEPARTMENT_COUNT;
                data(employment, employmentRow++, empNo(seq), deptCode(dept), positionCode(dept),
                        "否", SECONDARY_START, "");
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            workbook.write(buffer);
            return new ByteArrayInputStream(buffer.toByteArray());
        } catch (IOException ex) {
            throw new UncheckedIOException("构造导入工作簿失败", ex);
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

    private static String deptCode(int index) {
        return String.format("DEPT_%02d", index);
    }

    private static String positionCode(int index) {
        return String.format("POS_%02d", index);
    }

    private static String empNo(int seq) {
        return String.format("E%04d", seq);
    }

    /** 11 位且文件内唯一，避免触发「手机号在文件内重复」。 */
    private static String mobile(int seq) {
        return String.format("138%08d", seq);
    }

    private static String email(int seq) {
        return String.format("user%04d@example.com", seq);
    }

    // ---------------------------------------------------------------- 工具

    private OrgUnit requireOrg(String code) {
        return orgUnits.findByCode(code)
                .orElseThrow(() -> new AssertionError("组织单元未落库：" + code));
    }

    private Person requirePerson(String empNo) {
        return persons.findByEmpNo(empNo)
                .orElseThrow(() -> new AssertionError("人员未落库：" + empNo));
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return value == null ? -1 : value;
    }
}
