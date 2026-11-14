package com.foundation.authz.application;

import com.foundation.audit.api.AuditApi;
import com.foundation.audit.api.AuditReportItem;
import com.foundation.common.id.Ids;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Foundation 自身鉴权动作的审计写入（F-09 BAS-19 §审计）。
 *
 * <p>审计按应用编码 {@code fdn} 汇聚到 BAS-10 审计表；{@link AuditApi} 可选（缺失时静默跳过）。
 *
 * <p>写方法用 {@code REQUIRES_NEW}：互斥拒绝会在调用方抛出 {@code CONFLICT} 导致业务事务回滚，
 * 拒绝审计必须落在独立事务里才能留存。
 */
public class AuthzAuditWriter {

    private static final String APP_CODE = "fdn";

    private final ObjectProvider<AuditApi> audit;

    public AuthzAuditWriter(ObjectProvider<AuditApi> audit) {
        this.audit = audit;
    }

    /**
     * 写一条鉴权审计。
     *
     * @param action   动作码（如 {@code authz.role.mutex_denied}）
     * @param outcome  {@code SUCCESS} / {@code DENIED} / …
     * @param actorId  操作人
     * @param resource 资源标识（{@code type:id}）
     * @param detail   结果摘要
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(String action, String outcome, String actorId, String resource, String detail) {
        AuditApi api = audit.getIfAvailable();
        if (api == null) {
            return;
        }
        AuditReportItem item = new AuditReportItem(Ids.newId("evt"), Instant.now(), APP_CODE, action,
                resource, outcome, actorId, null, null, detail, 0L);
        api.report(null, List.of(item));
    }
}
