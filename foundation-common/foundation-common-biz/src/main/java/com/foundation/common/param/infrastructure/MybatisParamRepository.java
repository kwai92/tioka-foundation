package com.foundation.common.param.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.common.param.domain.Param;
import com.foundation.common.param.infrastructure.mapper.ParamMapper;
import com.foundation.common.param.infrastructure.persistence.ParamDO;

import java.util.Optional;

/** 参数仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisParamRepository implements ParamRepository {

    private final ParamMapper mapper;

    public MybatisParamRepository(ParamMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Param> find(String appCode, String paramKey) {
        LambdaQueryWrapper<ParamDO> wrapper = Wrappers.<ParamDO>lambdaQuery().eq(ParamDO::getParamKey, paramKey);
        if (appCode == null) {
            wrapper.isNull(ParamDO::getAppCode);
        } else {
            wrapper.eq(ParamDO::getAppCode, appCode);
        }
        return Optional.ofNullable(mapper.selectOne(wrapper)).map(MybatisParamRepository::toDomain);
    }

    @Override
    public void insert(Param param, String operator) {
        ParamDO row = new ParamDO();
        row.setId(param.id());
        row.setAppCode(param.appCode());
        row.setParamKey(param.paramKey());
        row.setParamValue(param.storedValue());
        row.setSecret(param.secret());
        row.setDescription(param.description());
        // 显式设置操作人；不设 updateAt，交由数据库 DEFAULT now()（语义同原 SQL）
        row.setUpdateBy(operator);
        mapper.insert(row);
    }

    @Override
    public void update(Param param, String operator) {
        mapper.update(null, Wrappers.<ParamDO>lambdaUpdate()
                .set(ParamDO::getParamValue, param.storedValue())
                .set(ParamDO::getSecret, param.secret())
                .set(ParamDO::getDescription, param.description())
                .set(ParamDO::getUpdateBy, operator)
                .setSql("update_at = now()")
                .eq(ParamDO::getId, param.id()));
    }

    private static Param toDomain(ParamDO row) {
        return new Param(row.getId(), row.getAppCode(), row.getParamKey(), row.getParamValue(),
                Boolean.TRUE.equals(row.getSecret()), row.getDescription());
    }
}
