package com.foundation.sync.infrastructure;

/**
 * 出网台账的占位实现（ADR-074 {@code ext_interaction}，F-08 BAS-11 §9）。
 *
 * <p>台账表由 ADR-074 定义，本仓库尚未落地（随 FND-07 出网白名单 / FND-12 插件宿主交付）；
 * 在其就绪前本实现不写任何记录，仅保证 {@link SyncLedgerWriter} 端口可装配。台账表就绪后
 * 提供 ADR-074 实现并把 {@code foundation.sync.egress.write-ledger} 置为 {@code true} 即启用。
 */
public class NoopSyncLedgerWriter implements SyncLedgerWriter {

    @Override
    public void write(SyncLedgerEntry entry) {
        // 台账表未落地（ADR-074），此处不落记录。
    }
}
