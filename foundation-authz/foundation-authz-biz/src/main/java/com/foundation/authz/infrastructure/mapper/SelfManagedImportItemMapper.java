package com.foundation.authz.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.authz.infrastructure.persistence.SelfManagedImportItemDO;
import org.apache.ibatis.annotations.Mapper;

/** 自管一次性导入明细项 Mapper（简单 CRUD 走 {@link BaseMapper}，48-CS §4）。 */
@Mapper
public interface SelfManagedImportItemMapper extends BaseMapper<SelfManagedImportItemDO> {
}
