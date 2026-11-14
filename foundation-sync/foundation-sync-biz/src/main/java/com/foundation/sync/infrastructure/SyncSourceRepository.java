package com.foundation.sync.infrastructure;

import com.foundation.sync.domain.SyncSource;
import com.foundation.sync.spi.SourceCapabilities;

import java.util.List;
import java.util.Optional;

/** 同步来源仓储（F-08 BAS-11 §12 {@code SyncSourceRepository}）。 */
public interface SyncSourceRepository {

    /** 全部来源（按编码升序）。 */
    List<SyncSource> list();

    Optional<SyncSource> find(String id);

    Optional<SyncSource> findByCode(String code);

    /** 当前权威来源（{@code authority=TRUE}），至多一个。 */
    Optional<SyncSource> findAuthority();

    void insert(SyncSource source, String operator);

    void update(SyncSource source, String operator);

    /** 探测后回写能力声明缓存。 */
    void updateCapabilities(String id, SourceCapabilities capabilities);
}
