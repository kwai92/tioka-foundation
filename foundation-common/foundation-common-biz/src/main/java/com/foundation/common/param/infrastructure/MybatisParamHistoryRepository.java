package com.foundation.common.param.infrastructure;

import com.foundation.common.id.CommonIdPrefix;
import com.foundation.common.id.Ids;
import com.foundation.common.param.infrastructure.mapper.ParamHistoryMapper;
import com.foundation.common.param.infrastructure.persistence.ParamHistoryDO;

/** 参数变更历史仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisParamHistoryRepository implements ParamHistoryRepository {

    private final ParamHistoryMapper mapper;

    public MybatisParamHistoryRepository(ParamHistoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void append(String paramId, String oldDigest, String newDigest, String operator) {
        ParamHistoryDO row = new ParamHistoryDO();
        row.setId(Ids.newId(CommonIdPrefix.PARAM_HISTORY));
        row.setParamId(paramId);
        row.setOldDigest(oldDigest);
        row.setNewDigest(newDigest);
        row.setChangedBy(operator);
        // 不设 changedAt，交由数据库 DEFAULT now()（语义同原 SQL）
        mapper.insert(row);
    }
}
