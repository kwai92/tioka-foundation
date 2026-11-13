package com.foundation.starter.client.org;

import java.util.List;
import java.util.Optional;

/**
 * 组织查询（F-07 §8）：按编码解析组织树，带本地缓存。
 *
 * <p>只读：组织的增删改在 Foundation 管理台，starter 不提供写接口。
 */
public interface OrgClient {

    /** 按编码解析组织单元。 */
    Optional<OrgUnit> byCode(String orgCode);

    /** 下级组织单元（含 / 不含本单元按参数）。 */
    List<OrgUnit> children(String orgCode);
}
