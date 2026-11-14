package com.foundation.audit.infrastructure;

import com.foundation.audit.domain.AuditSeal;

import java.util.Optional;

/** 审计链封印仓储（F-09 BAS-10，MyBatis-Plus）：追加封印、取链尾。 */
public interface AuditSealRepository {

    /** 追加一条封印。 */
    void save(AuditSeal seal);

    /** 取最近一条封印（链尾）；无则返回空。 */
    Optional<AuditSeal> latest();
}
