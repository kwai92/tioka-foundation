package com.foundation.common.param.infrastructure;

import com.foundation.common.param.domain.Param;

import java.util.Optional;

/** 参数仓储（作用域唯一：全局 {@code param_key} 唯一，应用内 {@code app_code + param_key} 唯一）。 */
public interface ParamRepository {

    Optional<Param> find(String appCode, String paramKey);

    void insert(Param param, String operator);

    void update(Param param, String operator);
}
