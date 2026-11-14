package com.foundation.audit.infrastructure;

import java.time.Instant;
import java.util.List;

/**
 * 审计归档存储 SPI（F-09 BAS-10 §归档）。
 *
 * <p>负责列出可归档（超出在线保留期）的月度分区并执行「解绑 + 删除」。默认实现为 PostgreSQL 物理分区维护。
 */
public interface AuditArchiveStore {

    /** 列出上界早于等于 {@code cutoff} 的分区名（归档候选）。 */
    List<String> listExpiredPartitions(Instant cutoff);

    /** 解绑并删除指定分区（分区名必须来自 {@link #listExpiredPartitions} 的白名单）。 */
    void detachAndDrop(String partition);
}
