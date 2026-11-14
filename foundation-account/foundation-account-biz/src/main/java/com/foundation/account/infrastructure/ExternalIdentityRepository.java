package com.foundation.account.infrastructure;

import com.foundation.account.api.dto.ExternalIdentitySource;
import com.foundation.account.domain.ExternalIdentity;

import java.util.List;
import java.util.Optional;

/** 外部身份仓储（F-04 §2，MyBatis-Plus）。 */
public interface ExternalIdentityRepository {

    Optional<ExternalIdentity> findById(String id);

    /** 按来源与外部 ID 取（{@code source}+{@code external_id} 联合唯一）。 */
    Optional<ExternalIdentity> findBySourceAndExternalId(ExternalIdentitySource source, String externalId);

    /** 待处理关联列表（{@code person_id IS NULL}）。 */
    List<ExternalIdentity> findPending();

    /** 某人员的外部身份列表。 */
    List<ExternalIdentity> findByPersonId(String personId);

    void insert(ExternalIdentity identity);

    void update(ExternalIdentity identity);
}
