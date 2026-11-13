package com.foundation.server;

import com.foundation.common.api.dict.DictItemCmd;
import com.foundation.common.api.dict.DictTypeCmd;
import com.foundation.common.api.param.ParamApi;
import com.foundation.common.api.param.ParamCmd;
import com.foundation.test.FoundationContainers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-02 BAS-09：写接口幂等 —— 重复提交不产生重复行。
 *
 * <p>写操作按「作用域 + 编码/键」天然幂等（部分唯一索引）。HTTP 层的 {@code Idempotency-Key}
 * 由 BAS-24 的 {@code foundation-starter-web} 统一处理。
 */
@SpringBootTest(properties = "foundation.dict.cache-ttl=0")
class DictIdempotencyIT {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        FoundationContainers.register(registry);
    }

    @Autowired
    private com.foundation.common.api.dict.DictApi dictApi;

    @Autowired
    private ParamApi paramApi;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanUp() {
        jdbc.execute("TRUNCATE fdn_dict_item, fdn_dict_type, fdn_param_history, fdn_param, fdn_outbox");
    }

    @Test
    void repeatedDictTypeWritesKeepSingleRow() {
        DictTypeCmd cmd = new DictTypeCmd(null, "order_status", "订单状态", null, "alice");
        dictApi.upsertType(cmd);
        dictApi.upsertType(cmd);

        assertThat(count("fdn_dict_type")).isEqualTo(1);
    }

    @Test
    void repeatedDictItemWritesKeepSingleRow() {
        dictApi.upsertType(new DictTypeCmd(null, "order_status", "订单状态", null, "alice"));
        DictItemCmd cmd = new DictItemCmd(null, "order_status", "NEW", "新建", 10, null, null, null, "alice");
        dictApi.upsertItem(cmd);
        dictApi.upsertItem(cmd);

        assertThat(count("fdn_dict_item")).isEqualTo(1);
    }

    @Test
    void repeatedParamWritesKeepSingleRow() {
        ParamCmd cmd = new ParamCmd(null, "site.title", "Tioka", null, "站点标题", "alice");
        paramApi.set(cmd);
        paramApi.set(cmd);

        assertThat(count("fdn_param")).isEqualTo(1);
        assertThat(paramApi.value(null, "site.title")).isEqualTo("Tioka");
    }

    private int count(String table) {
        Integer rows = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return rows == null ? 0 : rows;
    }
}
