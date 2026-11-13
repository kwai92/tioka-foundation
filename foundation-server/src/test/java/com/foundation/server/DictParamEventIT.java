package com.foundation.server;

import com.foundation.common.api.dict.DictApi;
import com.foundation.common.api.dict.DictItemCmd;
import com.foundation.common.api.dict.DictTypeCmd;
import com.foundation.common.api.event.CommonEventTypes;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-02 BAS-09：字典/参数变更落 {@code fdn_outbox}，事件体只带 ID、版本、变化类型。
 */
@SpringBootTest(properties = "foundation.dict.cache-ttl=0")
class DictParamEventIT {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        FoundationContainers.register(registry);
    }

    @Autowired
    private DictApi dictApi;

    @Autowired
    private ParamApi paramApi;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanUp() {
        jdbc.execute("TRUNCATE fdn_dict_item, fdn_dict_type, fdn_param_history, fdn_param, fdn_outbox");
    }

    @Test
    void dictChangeEmitsDictChangedEvent() {
        dictApi.upsertType(new DictTypeCmd(null, "order_status", "订单状态", null, "alice"));

        List<Map<String, Object>> events = events(CommonEventTypes.DICT_CHANGED);
        assertThat(events).hasSize(1);
        Map<String, Object> event = events.get(0);
        assertThat(event.get("topic")).isEqualTo("fdn-events");
        assertThat(event.get("status")).isEqualTo("NEW");
        assertThat(event.get("id")).isEqualTo(event.get("aggregate"));
        assertThat(event.get("payload")).asString().contains("\"changeType\"").contains("\"version\"");
    }

    @Test
    void paramChangeEmitsParamChangedEvent() {
        paramApi.set(new ParamCmd(null, "site.title", "Tioka", null, "站点标题", "alice"));

        List<Map<String, Object>> events = events(CommonEventTypes.PARAM_CHANGED);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("topic")).isEqualTo("fdn-events");
    }

    @Test
    void dictItemChangeEmitsEventForItemAggregate() {
        dictApi.upsertType(new DictTypeCmd(null, "order_status", "订单状态", null, "alice"));
        dictApi.upsertItem(new DictItemCmd(null, "order_status", "NEW", "新建", 10, null, null, null, "alice"));

        List<Map<String, Object>> events = events(CommonEventTypes.DICT_CHANGED);
        assertThat(events).hasSize(2);
        assertThat(events).extracting(event -> event.get("aggregate").toString())
                .allMatch(aggregate -> aggregate.startsWith("dt_") || aggregate.startsWith("di_"));
    }

    private List<Map<String, Object>> events(String eventType) {
        return jdbc.queryForList("SELECT id::text AS id, topic, event_type, aggregate, payload::text AS payload, status "
                + "FROM fdn_outbox WHERE event_type = ? ORDER BY id", eventType);
    }
}
