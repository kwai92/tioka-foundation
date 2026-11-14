package com.foundation.authz.infrastructure;

import com.foundation.authz.domain.AuthMode;
import com.foundation.authz.domain.Subsystem;

import java.util.List;
import java.util.Optional;

/** 子系统仓储（F-05 BAS-27 §子系统）。 */
public interface SubsystemRepository {

    /** 全部子系统。 */
    List<Subsystem> list();

    /** 按主键查询。 */
    Optional<Subsystem> findById(String id);

    /** 按所属应用查询（1:1）。 */
    Optional<Subsystem> findByAppId(String appId);

    /** 新增子系统。 */
    void insert(Subsystem subsystem);

    /** 变更授权模式。 */
    void updateAuthMode(String id, AuthMode authMode);
}
