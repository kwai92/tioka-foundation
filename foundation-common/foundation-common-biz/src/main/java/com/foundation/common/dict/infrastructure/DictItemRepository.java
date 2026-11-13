package com.foundation.common.dict.infrastructure;

import com.foundation.common.dict.domain.DictItem;

import java.util.List;
import java.util.Optional;

/** 字典项仓储。 */
public interface DictItemRepository {

    /** 某类型下的启用项，按 {@code sort_no}、{@code code} 升序。 */
    List<DictItem> listEnabled(String typeId);

    Optional<DictItem> find(String typeId, String code);

    void insert(DictItem item);

    void update(DictItem item);
}
