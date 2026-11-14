package com.foundation.account.domain;

import com.foundation.account.api.dto.ExternalIdentitySource;
import com.foundation.account.api.dto.ExternalIdentityView;
import com.foundation.account.api.dto.LinkMode;

import java.time.Instant;

/**
 * 外部身份（F-04 §2）：外部身份 → 人员的关联。
 *
 * @param id         主键（{@code xid_…}）
 * @param source     来源
 * @param externalId 外部 ID（与来源联合唯一）
 * @param personId   关联人员 ID；为空即一条待处理关联
 * @param linkMode   关联方式；待处理时为空
 * @param linkedAt   关联时间；待处理时为空
 */
public record ExternalIdentity(String id,
                               ExternalIdentitySource source,
                               String externalId,
                               String personId,
                               LinkMode linkMode,
                               Instant linkedAt) {

    /** 转对外视图。 */
    public ExternalIdentityView toView() {
        return new ExternalIdentityView(id, source, externalId, personId, linkMode, linkedAt);
    }

    /** 是否待处理（未关联人员）。 */
    public boolean pending() {
        return personId == null || personId.isBlank();
    }

    /** 建立关联。 */
    public ExternalIdentity link(String targetPersonId, LinkMode mode, Instant at) {
        return new ExternalIdentity(id, source, externalId, targetPersonId, mode, at);
    }

    /** 解除关联，回到待处理状态（F-04 §2 规则 4）。 */
    public ExternalIdentity unlink() {
        return new ExternalIdentity(id, source, externalId, null, null, null);
    }
}
