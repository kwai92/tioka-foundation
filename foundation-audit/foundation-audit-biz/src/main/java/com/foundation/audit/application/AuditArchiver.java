package com.foundation.audit.application;

import com.foundation.audit.api.RetentionClass;
import com.foundation.audit.domain.AuditLog;
import com.foundation.audit.infrastructure.AuditArchiveStore;
import com.foundation.audit.infrastructure.AuditLogRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 审计归档器（F-09 BAS-10 §归档）：归档超出在线保留期的月度分区，并写一条运维审计。
 */
public class AuditArchiver {

    private final AuditArchiveStore store;
    private final AuditLogRepository auditLogs;

    public AuditArchiver(AuditArchiveStore store, AuditLogRepository auditLogs) {
        this.store = store;
        this.auditLogs = auditLogs;
    }

    /** 归档上界早于等于 {@code cutoff} 的分区；返回归档分区数。 */
    public int archiveExpired(Instant cutoff) {
        List<String> expired = store.listExpiredPartitions(cutoff);
        for (String partition : expired) {
            store.detachAndDrop(partition);
            auditLogs.save(AuditLog.self("fdn", "audit.partition.archived", null, partition,
                    RetentionClass.OPS, Map.of("partition", partition)));
        }
        return expired.size();
    }
}
