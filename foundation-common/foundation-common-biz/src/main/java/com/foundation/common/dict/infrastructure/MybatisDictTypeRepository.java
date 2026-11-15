package com.foundation.common.dict.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.common.dict.domain.DictType;
import com.foundation.common.dict.infrastructure.mapper.DictTypeMapper;
import com.foundation.common.dict.infrastructure.persistence.DictTypeDO;

import java.util.Optional;

/** 字典类型仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisDictTypeRepository implements DictTypeRepository {

    private final DictTypeMapper mapper;

    public MybatisDictTypeRepository(DictTypeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<DictType> find(String appCode, String code) {
        LambdaQueryWrapper<DictTypeDO> wrapper = Wrappers.<DictTypeDO>lambdaQuery().eq(DictTypeDO::getCode, code);
        if (appCode == null) {
            wrapper.isNull(DictTypeDO::getAppCode);
        } else {
            wrapper.eq(DictTypeDO::getAppCode, appCode);
        }
        return Optional.ofNullable(mapper.selectOne(wrapper)).map(MybatisDictTypeRepository::toDomain);
    }

    @Override
    public void insert(DictType type, String operator) {
        DictTypeDO row = new DictTypeDO();
        row.setId(type.id());
        row.setCode(type.code());
        row.setName(type.name());
        row.setAppCode(type.appCode());
        row.setStatus(type.status());
        // 显式设置操作人：AuditMetaObjectHandler 仅在字段为 null 时填充，显式值胜出（语义同原 SQL）
        row.setCreateBy(operator);
        row.setUpdateBy(operator);
        mapper.insert(row);
    }

    @Override
    public void update(DictType type, String operator) {
        mapper.update(null, Wrappers.<DictTypeDO>lambdaUpdate()
                .set(DictTypeDO::getName, type.name())
                .set(DictTypeDO::getStatus, type.status())
                .set(DictTypeDO::getUpdateBy, operator)
                .setSql("update_at = now()")
                .eq(DictTypeDO::getId, type.id()));
    }

    private static DictType toDomain(DictTypeDO row) {
        return new DictType(row.getId(), row.getAppCode(), row.getCode(), row.getName(), row.getStatus());
    }
}
