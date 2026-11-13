package com.foundation.common.dict.infrastructure;

import com.foundation.common.dict.domain.DictType;

import java.util.Optional;

/** 字典类型仓储（作用域唯一：全局 {@code code} 唯一，应用内 {@code app_code + code} 唯一）。 */
public interface DictTypeRepository {

    Optional<DictType> find(String appCode, String code);

    void insert(DictType type, String operator);

    void update(DictType type, String operator);
}
