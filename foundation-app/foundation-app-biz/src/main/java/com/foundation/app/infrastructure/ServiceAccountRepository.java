package com.foundation.app.infrastructure;

import com.foundation.app.domain.ServiceAccount;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 服务账号仓储（F-05 BAS-04 §迁移 V3 {@code fdn_service_account}）。 */
public interface ServiceAccountRepository {

    List<ServiceAccount> listByAppId(String appId);

    Optional<ServiceAccount> findById(String id);

    Optional<ServiceAccount> findByName(String appId, String name);

    void insert(ServiceAccount serviceAccount, String operator);

    void updateStatus(String id, String status, String operator);

    /** 把到期仍为 {@code ACTIVE} 的服务账号置 {@code DISABLED}，返回处理条数。 */
    int disableExpired(Instant now);
}
