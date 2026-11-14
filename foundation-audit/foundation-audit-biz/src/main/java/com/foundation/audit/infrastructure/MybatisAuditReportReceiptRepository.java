package com.foundation.audit.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.audit.domain.AuditIdPrefix;
import com.foundation.audit.infrastructure.mapper.AuditReportReceiptMapper;
import com.foundation.audit.infrastructure.persistence.AuditReportReceiptDO;
import com.foundation.common.id.Ids;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/** 上报批次回执仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisAuditReportReceiptRepository implements AuditReportReceiptRepository {

    private final AuditReportReceiptMapper mapper;

    public MybatisAuditReportReceiptRepository(AuditReportReceiptMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Integer> findAccepted(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        AuditReportReceiptDO row = mapper.selectOne(Wrappers.<AuditReportReceiptDO>lambdaQuery()
                .eq(AuditReportReceiptDO::getIdempotencyKey, idempotencyKey));
        return Optional.ofNullable(row).map(AuditReportReceiptDO::getAccepted);
    }

    @Override
    public void save(String idempotencyKey, String appCode, int accepted) {
        AuditReportReceiptDO row = new AuditReportReceiptDO();
        row.setId(Ids.newId(AuditIdPrefix.REPORT));
        row.setIdempotencyKey(idempotencyKey);
        row.setAppCode(appCode);
        row.setAccepted(accepted);
        row.setCreateAt(LocalDateTime.now(ZoneOffset.UTC));
        mapper.insert(row);
    }
}
