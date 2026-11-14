package com.foundation.account.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.account.infrastructure.persistence.AccountReconcileDO;
import org.apache.ibatis.annotations.Mapper;

/** 每日对账记录 Mapper（简单 CRUD 走 {@link BaseMapper}，48-CS §4）。 */
@Mapper
public interface AccountReconcileMapper extends BaseMapper<AccountReconcileDO> {
}
