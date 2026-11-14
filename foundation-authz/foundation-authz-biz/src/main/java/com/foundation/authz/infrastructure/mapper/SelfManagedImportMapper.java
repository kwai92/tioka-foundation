package com.foundation.authz.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.authz.infrastructure.persistence.SelfManagedImportDO;
import org.apache.ibatis.annotations.Mapper;

/** 自管一次性导入批次 Mapper（简单 CRUD 走 {@link BaseMapper}，48-CS §4）。 */
@Mapper
public interface SelfManagedImportMapper extends BaseMapper<SelfManagedImportDO> {
}
