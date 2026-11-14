package com.foundation.account.application;

import com.foundation.account.api.ExternalIdentityApi;
import com.foundation.account.api.dto.ExternalIdentityRecordCmd;
import com.foundation.account.api.dto.ExternalIdentitySource;
import com.foundation.account.api.dto.ExternalIdentityView;

import java.util.List;
import java.util.Optional;

/**
 * {@link ExternalIdentityApi} 契约实现（F-04 §2）：供同步、首次登录与离职清理流程依赖。
 */
public class ExternalIdentityApiAdapter implements ExternalIdentityApi {

    private final ExternalIdentityApplicationService identities;

    public ExternalIdentityApiAdapter(ExternalIdentityApplicationService identities) {
        this.identities = identities;
    }

    @Override
    public Optional<ExternalIdentityView> findBySourceAndExternalId(ExternalIdentitySource source, String externalId) {
        return identities.findBySourceAndExternalId(source, externalId);
    }

    @Override
    public List<ExternalIdentityView> listPending() {
        return identities.listPending();
    }

    @Override
    public List<ExternalIdentityView> listByPerson(String personId) {
        return identities.listByPerson(personId);
    }

    @Override
    public String record(ExternalIdentityRecordCmd cmd) {
        return identities.record(cmd);
    }

    @Override
    public void confirm(String externalIdentityId, String personId, String operator) {
        identities.confirm(externalIdentityId, personId, operator);
    }

    @Override
    public void unlink(String externalIdentityId, String operator) {
        identities.unlink(externalIdentityId, operator);
    }
}
