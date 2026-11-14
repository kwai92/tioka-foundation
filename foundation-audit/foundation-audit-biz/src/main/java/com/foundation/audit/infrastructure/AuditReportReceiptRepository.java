package com.foundation.audit.infrastructure;

import java.util.Optional;

/** 上报批次回执仓储（F-09 BAS-10，MyBatis-Plus）：幂等查询与写入。 */
public interface AuditReportReceiptRepository {

    /** 按幂等键取当时的接收条数；不存在返回空。 */
    Optional<Integer> findAccepted(String idempotencyKey);

    /** 记录一次上报批次回执。 */
    void save(String idempotencyKey, String appCode, int accepted);
}
