package com.foundation.org.api;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.org.api.dto.ImportResult;
import com.foundation.org.api.dto.OrgStatus;
import com.foundation.org.api.dto.OrgUnitType;
import com.foundation.org.api.dto.PersonStatus;
import com.foundation.org.api.dto.PersonType;
import com.foundation.org.application.OrgExportService;
import com.foundation.org.application.OrgImportService;
import com.foundation.starter.security.permission.RequirePermission;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * 组织与人员导入 / 导出 REST（F-03 §4）。
 *
 * <p>导入整表全量校验、任一行失败整体拒绝（不半量落库）；导出默认脱敏，明文由
 * {@code foundation.org.export.include-sensitive} 控制。写接口的 {@code Idempotency-Key}
 * 由 {@code foundation-starter-web}（BAS-24）统一处理。
 */
@RestController
@RequestMapping("/api/fdn/org")
public class OrgImportController {

    /** 导出文件 MIME（xlsx）。 */
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final OrgImportService imports;
    private final OrgExportService exports;

    public OrgImportController(OrgImportService imports, OrgExportService exports) {
        this.imports = imports;
        this.exports = exports;
    }

    /** Excel 导入（F-03 §4.1）：整表校验后按业务键 upsert，返回可查询的导入结果。 */
    @PostMapping("/import")
    @RequirePermission("org:import")
    public ImportResult importWorkbook(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "导入文件不能为空");
        }
        try (var input = file.getInputStream()) {
            return imports.importWorkbook(input);
        } catch (IOException ex) {
            throw new UncheckedIOException("读取导入文件失败", ex);
        }
    }

    /** 导入任务结果查询（F-03 §4.1）。 */
    @GetMapping("/import/{taskId}")
    @RequirePermission("org:import")
    public ImportResult importResult(@PathVariable String taskId) {
        return imports.result(taskId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "导入任务不存在：" + taskId));
    }

    /** 按条件导出（F-03 §4.2）：默认脱敏，可回灌为导入模板。 */
    @GetMapping("/export")
    @RequirePermission("org:export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) OrgUnitType orgType,
                                         @RequestParam(required = false) OrgStatus orgStatus,
                                         @RequestParam(required = false) PersonType personType,
                                         @RequestParam(required = false) PersonStatus personStatus,
                                         @RequestParam(required = false) String orgUnitId) {
        byte[] body = exports.export(q, orgType, orgStatus, personType, personStatus, orgUnitId);
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"org-export.xlsx\"")
                .body(body);
    }
}
