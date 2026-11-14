package com.foundation.account.api;

import com.foundation.account.api.dto.ExternalIdentityRecordCmd;
import com.foundation.account.api.dto.ExternalIdentitySource;
import com.foundation.account.api.dto.ExternalIdentityView;

import java.util.List;
import java.util.Optional;

/**
 * 外部身份契约（F-04 §2）：记录「外部身份 → 人员」的关联。
 *
 * <p>自动关联只按工号或已验证邮箱且唯一时建立；**无姓名匹配、无自动建人**。
 */
public interface ExternalIdentityApi {

    /** 按来源与外部 ID 取外部身份。 */
    Optional<ExternalIdentityView> findBySourceAndExternalId(ExternalIdentitySource source, String externalId);

    /** 待处理关联列表（{@code person_id IS NULL}）。 */
    List<ExternalIdentityView> listPending();

    /** 某人员的外部身份列表。 */
    List<ExternalIdentityView> listByPerson(String personId);

    /**
     * 记录外部身份：已存在则返回既有记录；否则新建并按工号或已验证邮箱尝试自动关联
     * （匹配唯一时 {@code link_mode=AUTO}，否则留待处理）。
     *
     * @return 外部身份 ID（{@code xid_…}）
     */
    String record(ExternalIdentityRecordCmd cmd);

    /** 管理员确认关联（{@code link_mode=ADMIN}）；未匹配人员时按工号或已验证邮箱重新匹配。 */
    void confirm(String externalIdentityId, String personId, String operator);

    /** 解除关联，外部身份回到待处理状态。 */
    void unlink(String externalIdentityId, String operator);
}
