package com.foundation.app.infrastructure;

import com.foundation.app.domain.AppClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 应用客户端仓储（F-05 BAS-04 §迁移 V3 {@code fdn_app_client}）。 */
public interface AppClientRepository {

    List<AppClient> listByAppId(String appId);

    Optional<AppClient> find(String appId, String clientId);

    Optional<AppClient> findById(String id);

    void insert(AppClient client, String operator);

    /** 更新客户端状态（登记完成 {@code ACTIVE}、停用 {@code DISABLED}）。 */
    void updateStatus(String id, String status, String operator);

    /** 记录密钥轮换时间；密钥明文不落库。 */
    void markSecretRotated(String id, Instant rotatedAt, String operator);
}
