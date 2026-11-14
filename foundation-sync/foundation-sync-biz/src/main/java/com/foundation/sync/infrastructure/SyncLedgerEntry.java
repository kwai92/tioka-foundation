package com.foundation.sync.infrastructure;

import java.time.Instant;

/**
 * 出网台账条目（ADR-074 {@code ext_interaction}，F-08 BAS-11 §9）。
 *
 * <p>每次同步访问来源都留一条 OUT 方向记录：{@code counterparty} = 来源实例、
 * {@code endpoint} = 主机 + 路径模板、{@code operation} = 如 {@code sync.orgUnit.fetch}。
 *
 * @param counterparty  对端（来源实例）
 * @param endpoint      端点（主机 + 路径模板）
 * @param operation     操作名，如 {@code sync.orgUnit.fetch}
 * @param identityMode  身份模式（如 {@code APP_TOKEN} / {@code BASIC}）
 * @param credentialId  凭证引用（只存引用）
 * @param result        结果（{@code OK} / {@code FAILED}）
 * @param retryCount    重试次数
 * @param occurredAt    发生时间
 */
public record SyncLedgerEntry(String counterparty,
                              String endpoint,
                              String operation,
                              String identityMode,
                              String credentialId,
                              String result,
                              int retryCount,
                              Instant occurredAt) {
}
