package com.foundation.sync.infrastructure;

/**
 * 出网台账写入（ADR-074 {@code ext_interaction}，F-08 BAS-11 §9）。
 *
 * <p>台账表由 ADR-074 定义，本仓库尚未落地（随 FND-07 出网白名单 / FND-12 插件宿主交付）；
 * 因此这里只固定写入端口，默认实现为 {@link NoopSyncLedgerWriter}。台账表就绪后，
 * 提供本接口的 ADR-074 实现并把 {@code foundation.sync.egress.write-ledger} 置为 {@code true} 即启用。
 */
public interface SyncLedgerWriter {

    /** 记录一次来源访问（须在业务事务内调用）。 */
    void write(SyncLedgerEntry entry);
}
