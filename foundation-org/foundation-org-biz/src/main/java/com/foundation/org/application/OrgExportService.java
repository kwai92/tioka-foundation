package com.foundation.org.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.org.api.dto.OrgStatus;
import com.foundation.org.api.dto.OrgUnitType;
import com.foundation.org.api.dto.PersonStatus;
import com.foundation.org.api.dto.PersonType;
import com.foundation.org.config.OrgExportProperties;
import com.foundation.org.domain.Employment;
import com.foundation.org.domain.OrgUnit;
import com.foundation.org.domain.Person;
import com.foundation.org.infrastructure.EmploymentRepository;
import com.foundation.org.infrastructure.OrgUnitRepository;
import com.foundation.org.infrastructure.PersonRepository;
import com.foundation.org.infrastructure.PositionRepository;
import com.foundation.org.infrastructure.person.PersonCipher;
import com.foundation.org.infrastructure.person.PersonMasker;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Excel 导出应用服务（F-03 §4.2、§7.3）。
 *
 * <p>按筛选条件导出组织单元 / 人员 / 任职三个工作表，列序与导入模板一致（导出文件可直接回灌
 * {@code POST /api/fdn/org/import}）。单次行数合计不超过 {@code foundation.org.export.max-rows}；
 * 手机 / 邮箱默认导出脱敏值，{@code foundation.org.export.include-sensitive=true} 时导出明文。
 */
public class OrgExportService {

    private static final String SHEET_ORG_UNIT = "组织单元";
    private static final String SHEET_PERSON = "人员";
    private static final String SHEET_EMPLOYMENT = "任职";

    private static final String[] ORG_UNIT_HEADERS =
            {"编码", "名称", "类型", "上级编码", "排序", "负责人工号"};
    private static final String[] PERSON_HEADERS = {"工号", "姓名", "类型", "手机", "邮箱"};
    private static final String[] EMPLOYMENT_HEADERS =
            {"工号", "组织单元编码", "岗位编码", "是否主任职", "开始日期", "结束日期"};

    private final OrgUnitRepository orgUnits;
    private final PersonRepository persons;
    private final EmploymentRepository employments;
    private final PositionRepository positions;
    private final PersonCipher cipher;
    private final PersonMasker masker;
    private final OrgExportProperties properties;

    public OrgExportService(OrgUnitRepository orgUnits, PersonRepository persons,
                            EmploymentRepository employments, PositionRepository positions,
                            PersonCipher cipher, PersonMasker masker, OrgExportProperties properties) {
        this.orgUnits = orgUnits;
        this.persons = persons;
        this.employments = employments;
        this.positions = positions;
        this.cipher = cipher;
        this.masker = masker;
        this.properties = properties;
    }

    /**
     * 导出为 xlsx 字节。
     *
     * @param q          组织单元 / 人员关键字（名称或编码 / 工号模糊），可空
     * @param orgType    组织单元类型筛选，可空
     * @param orgStatus  组织单元状态筛选，可空
     * @param personType 人员类型筛选，可空
     * @param personStatus 人员状态筛选，可空
     * @param orgUnitId  人员所在组织单元（含下级），可空
     */
    public byte[] export(String q, OrgUnitType orgType, OrgStatus orgStatus,
                         PersonType personType, PersonStatus personStatus, String orgUnitId) {
        int maxRows = Math.max(1, properties.getMaxRows());
        List<OrgUnit> orgRows = orgUnits.search(q, orgType, orgStatus, 1, maxRows);
        List<Person> personRows = persons.search(q, personType, personStatus, restrictPersonIds(orgUnitId),
                1, maxRows);
        List<Employment> employmentRows = employmentsOf(personRows);
        long total = (long) orgRows.size() + personRows.size() + employmentRows.size();
        if (total > maxRows) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "导出结果 " + total + " 行超过上限 " + maxRows + "，请收窄筛选条件");
        }

        Map<String, String> orgCodeById = new HashMap<>();
        orgUnits.findAll().forEach(unit -> orgCodeById.put(unit.id(), unit.code()));
        Map<String, String> empNoById = new HashMap<>();
        persons.findAll().forEach(person -> empNoById.put(person.id(), person.empNo()));
        Map<String, String> positionCodeById = new HashMap<>();
        positions.list(1, maxRows).forEach(position -> positionCodeById.put(position.id(), position.code()));

        boolean plain = properties.isIncludeSensitive();
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeOrgUnits(workbook.createSheet(SHEET_ORG_UNIT), orgRows, orgCodeById, empNoById);
            writePersons(workbook.createSheet(SHEET_PERSON), personRows, plain);
            writeEmployments(workbook.createSheet(SHEET_EMPLOYMENT), employmentRows, empNoById,
                    orgCodeById, positionCodeById);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("导出 Excel 写入失败", ex);
        }
    }

    private void writeOrgUnits(Sheet sheet, List<OrgUnit> rows, Map<String, String> codeById,
                               Map<String, String> empNoById) {
        writeRow(sheet, 0, (Object[]) ORG_UNIT_HEADERS);
        int index = 1;
        for (OrgUnit unit : rows) {
            String parentCode = unit.parentId() == null ? null : codeById.get(unit.parentId());
            String leaderEmpNo = unit.leaderPersonId() == null ? null : empNoById.get(unit.leaderPersonId());
            writeRow(sheet, index++, unit.code(), unit.name(), unit.type().name(), parentCode,
                    unit.sortNo(), leaderEmpNo);
        }
    }

    private void writePersons(Sheet sheet, List<Person> rows, boolean plain) {
        writeRow(sheet, 0, (Object[]) PERSON_HEADERS);
        int index = 1;
        for (Person person : rows) {
            String mobile = cipher.decrypt(person.mobileCipher());
            String email = cipher.decrypt(person.emailCipher());
            if (!plain) {
                mobile = masker.maskMobile(mobile);
                email = masker.maskEmail(email);
            }
            writeRow(sheet, index++, person.empNo(), person.name(), person.type().name(), mobile, email);
        }
    }

    private void writeEmployments(Sheet sheet, List<Employment> rows, Map<String, String> empNoById,
                                  Map<String, String> orgCodeById, Map<String, String> positionCodeById) {
        writeRow(sheet, 0, (Object[]) EMPLOYMENT_HEADERS);
        int index = 1;
        for (Employment employment : rows) {
            writeRow(sheet, index++,
                    empNoById.get(employment.personId()),
                    orgCodeById.get(employment.orgUnitId()),
                    employment.positionId() == null ? null : positionCodeById.get(employment.positionId()),
                    employment.primary() ? "是" : "否",
                    employment.startDate() == null ? null : employment.startDate().toString(),
                    employment.endDate() == null ? null : employment.endDate().toString());
        }
    }

    /** 导出人员对应的任职（含历史）。 */
    private List<Employment> employmentsOf(List<Person> personRows) {
        List<Employment> rows = new ArrayList<>();
        for (Person person : personRows) {
            rows.addAll(employments.byPerson(person.id()));
        }
        return rows;
    }

    private Collection<String> restrictPersonIds(String orgUnitId) {
        if (orgUnitId == null || orgUnitId.isBlank()) {
            return null;
        }
        Set<String> subtree = orgUnits.descendantIds(orgUnitId);
        return subtree.isEmpty() ? Set.of() : employments.openPersonIdsInOrgUnits(subtree);
    }

    private static void writeRow(Sheet sheet, int index, Object... values) {
        Row row = sheet.createRow(index);
        for (int column = 0; column < values.length; column++) {
            Cell cell = row.createCell(column);
            Object value = values[column];
            cell.setCellValue(value == null ? "" : String.valueOf(value));
        }
    }
}
