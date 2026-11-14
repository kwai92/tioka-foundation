package com.foundation.authz.domain;

/**
 * 自管一次性导入明细项（F-05 BAS-27 §自管导入）。
 *
 * @param id          明细项 ID（{@code mpi_{ulid}}）
 * @param batchId     所属批次 ID
 * @param kind        类型，见 {@link SelfImportKind}
 * @param externalKey 外部唯一键
 * @param payload     明细载荷（JSON）
 */
public record SelfManagedImportItem(String id,
                                    String batchId,
                                    SelfImportKind kind,
                                    String externalKey,
                                    String payload) {
}
