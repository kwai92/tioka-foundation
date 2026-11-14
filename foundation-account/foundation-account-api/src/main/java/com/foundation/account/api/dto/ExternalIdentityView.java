package com.foundation.account.api.dto;

import java.time.Instant;

/**
 * 外部身份视图（F-04 §2）。
 *
 * @param id         外部身份 ID（{@code xid_…}）
 * @param source     来源
 * @param externalId 外部 ID
 * @param personId   关联人员 ID；为空表示待处理关联
 * @param linkMode   关联方式；待处理时为空
 * @param linkedAt   关联时间；待处理时为空
 */
public record ExternalIdentityView(String id,
                                   ExternalIdentitySource source,
                                   String externalId,
                                   String personId,
                                   LinkMode linkMode,
                                   Instant linkedAt) {
}
