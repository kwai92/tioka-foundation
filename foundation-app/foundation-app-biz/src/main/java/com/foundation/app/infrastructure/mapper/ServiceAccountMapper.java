package com.foundation.app.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.app.infrastructure.persistence.ServiceAccountDO;
import org.apache.ibatis.annotations.Mapper;

/** {@code fdn_service_account} Mapper（简单 CRUD 走 Wrapper，无需自定义 SQL）。 */
@Mapper
public interface ServiceAccountMapper extends BaseMapper<ServiceAccountDO> {
}
