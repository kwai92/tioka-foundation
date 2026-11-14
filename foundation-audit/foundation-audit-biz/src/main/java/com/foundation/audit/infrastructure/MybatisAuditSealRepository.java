package com.foundation.audit.infrastructure;

import com.foundation.audit.domain.AuditSeal;
import com.foundation.audit.infrastructure.mapper.AuditSealMapper;
import com.foundation.audit.infrastructure.persistence.AuditSealDO;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/** 审计链封印仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisAuditSealRepository implements AuditSealRepository {

    private final AuditSealMapper mapper;

    public MybatisAuditSealRepository(AuditSealMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(AuditSeal seal) {
        AuditSealDO row = new AuditSealDO();
        row.setId(seal.id());
        row.setWindowFrom(LocalDateTime.ofInstant(seal.windowFrom(), ZoneOffset.UTC));
        row.setWindowTo(LocalDateTime.ofInstant(seal.windowTo(), ZoneOffset.UTC));
        row.setPrevHash(seal.prevHash());
        row.setBatchHash(seal.batchHash());
        row.setSealedAt(LocalDateTime.ofInstant(seal.sealedAt(), ZoneOffset.UTC));
        mapper.insert(row);
    }

    @Override
    public Optional<AuditSeal> latest() {
        return Optional.ofNullable(mapper.latest()).map(MybatisAuditSealRepository::toDomain);
    }

    private static AuditSeal toDomain(AuditSealDO row) {
        return new AuditSeal(row.getId(), row.getWindowFrom().toInstant(ZoneOffset.UTC),
                row.getWindowTo().toInstant(ZoneOffset.UTC), row.getPrevHash(), row.getBatchHash(),
                row.getSealedAt().toInstant(ZoneOffset.UTC));
    }
}
