package com.foundation.common.param.infrastructure;

import com.foundation.common.id.CommonIdPrefix;
import com.foundation.common.id.Ids;
import org.springframework.jdbc.core.JdbcTemplate;

/** 参数变更历史仓储的 JDBC 实现。 */
public class JdbcParamHistoryRepository implements ParamHistoryRepository {

    private final JdbcTemplate jdbc;

    public JdbcParamHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(String paramId, String oldDigest, String newDigest, String operator) {
        jdbc.update("INSERT INTO fdn_param_history (id, param_id, old_digest, new_digest, changed_by) "
                        + "VALUES (?, ?, ?, ?, ?)",
                Ids.newId(CommonIdPrefix.PARAM_HISTORY), paramId, oldDigest, newDigest, operator);
    }
}
