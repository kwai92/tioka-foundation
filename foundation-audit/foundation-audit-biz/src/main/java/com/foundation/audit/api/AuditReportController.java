package com.foundation.audit.api;

import com.foundation.starter.security.permission.RequirePermission;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 审计上报 REST（F-09 BAS-10 §接口 {@code /api/fdn/audit}）：批量接收审计事件。
 *
 * <p>路径 {@code /api/fdn/audit/events:batch} 与已提交的 {@code foundation-starter-audit} 的
 * {@code HttpAuditTransport.PATH} 完全一致（真实客户端契约）。
 *
 * <p>本类不标 {@code @RestController}：由 {@code AuditAutoConfiguration} 以 {@code @Bean} 装配，
 * 与 {@code AuditApi} 同生命周期（无持久化上下文时不注册）。
 */
@ResponseBody
@RequestMapping("/api/fdn/audit")
public class AuditReportController {

    private final AuditApi audit;

    public AuditReportController(AuditApi audit) {
        this.audit = audit;
    }

    /** 批量上报（按 {@code Idempotency-Key} 幂等）。 */
    @PostMapping("/events:batch")
    @RequirePermission("fdn:audit:report")
    public AuditReportResult report(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ReportRequest request) {
        List<AuditReportItem> events = request == null ? null : request.events();
        int accepted = audit.report(idempotencyKey, events == null ? List.of() : events);
        return new AuditReportResult(accepted);
    }

    /** 上报请求体。 */
    public record ReportRequest(List<AuditReportItem> events) {
    }
}
