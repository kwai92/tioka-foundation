package com.foundation.sync.scim.infrastructure;

import com.foundation.sync.scim.domain.ScimToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** SCIM 令牌仓储（F-08 BAS-13 §5 {@code fdn_scim_token}）。 */
public interface ScimTokenRepository {

    void insert(ScimToken token);

    /** 全部令牌（按创建时间倒序）。 */
    List<ScimToken> list();

    Optional<ScimToken> find(String id);

    /** 按识别前缀定位令牌（校验时先取行，再常量时间比对哈希）。 */
    Optional<ScimToken> findByPrefix(String prefix);

    /** 记录最近使用时间。 */
    void touchLastUsed(String id, Instant at);

    void delete(String id);
}
