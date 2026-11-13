package com.foundation.authz.infrastructure;

import com.foundation.authz.domain.RoleMutex;

import java.util.List;

/** 互斥角色仓储（F-05 BAS-05 §有效角色与互斥）。 */
public interface RoleMutexRepository {

    /** 全部互斥对（无序，由校验器归一为对称关系）。 */
    List<RoleMutex> all();
}
