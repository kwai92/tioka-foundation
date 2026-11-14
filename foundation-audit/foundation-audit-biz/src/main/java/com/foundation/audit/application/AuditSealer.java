package com.foundation.audit.application;

import com.foundation.audit.domain.AuditIdPrefix;
import com.foundation.audit.domain.AuditLog;
import com.foundation.audit.domain.AuditSeal;
import com.foundation.audit.infrastructure.AuditLogRepository;
import com.foundation.audit.infrastructure.AuditSealRepository;
import com.foundation.common.id.Ids;

import java.time.Instant;
import java.util.List;

/**
 * 审计链封印器（F-09 BAS-10；GOV-09 / ADR-072 §8）：把窗口内审计行串联进哈希链。
 *
 * <p>即使窗口内无行也写封印，保证链连续（窗口跳跃可被链式校验发现）。
 */
public class AuditSealer {

    private final AuditLogRepository auditLogs;
    private final AuditSealRepository seals;

    public AuditSealer(AuditLogRepository auditLogs, AuditSealRepository seals) {
        this.auditLogs = auditLogs;
        this.seals = seals;
    }

    /** 对 {@code [from, to)} 窗口内的审计行做一次封印并落库；返回新封印。 */
    public AuditSeal sealWindow(Instant from, Instant to) {
        List<AuditLog> rows = auditLogs.listByWindow(from, to);
        String prevHash = seals.latest().map(AuditSeal::batchHash).orElse(null);
        String batchHash = AuditSeal.chainHash(prevHash, rows);
        AuditSeal seal = new AuditSeal(Ids.newId(AuditIdPrefix.SEAL), from, to, prevHash, batchHash,
                Instant.now());
        seals.save(seal);
        return seal;
    }
}
