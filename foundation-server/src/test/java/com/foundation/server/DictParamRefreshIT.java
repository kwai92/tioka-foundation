package com.foundation.server;

import com.foundation.common.api.dict.DictApi;
import com.foundation.common.api.dict.DictItemCmd;
import com.foundation.common.api.dict.DictItemView;
import com.foundation.common.api.dict.DictTypeCmd;
import com.foundation.common.api.event.CommonEventTypes;
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
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-02 BAS-09：桩应用订阅变更事件 → 本地缓存失效 → 再查取到新值。
 *
 * <p>真实 starter 侧缓存与订阅在 BAS-08 实现，本用例用桩验证「事件足以驱动刷新」。
 */
@SpringBootTest(properties = "foundation.dict.cache-ttl=0")
class DictParamRefreshIT {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        FoundationContainers.register(registry);
    }

    @Autowired
    private DictApi dictApi;

    @Autowired
    private JdbcTemplate jdbc;

    private StubDictionaryClient stubApp;

    @BeforeEach
    void cleanUp() {
        jdbc.execute("TRUNCATE fdn_dict_item, fdn_dict_type, fdn_param_history, fdn_param, fdn_outbox");
        stubApp = new StubDictionaryClient(dictApi, jdbc);
    }

    @Test
    void stubAppSeesNewValueAfterConsumingChangeEvent() {
        dictApi.upsertType(new DictTypeCmd(null, "order_status", "订单状态", null, "alice"));
        dictApi.upsertItem(new DictItemCmd(null, "order_status", "NEW", "新建", 10, null, null, null, "alice"));
        assertThat(stubApp.labelOf(null, "order_status", "NEW")).isEqualTo("新建");

        dictApi.upsertItem(new DictItemCmd(null, "order_status", "NEW", "新建（修订）", 10, null, null, null, "bob"));
        // 未消费事件前，桩应用仍读到本地缓存
        assertThat(stubApp.labelOf(null, "order_status", "NEW")).isEqualTo("新建");

        assertThat(stubApp.consumePendingEvents()).isPositive();

        assertThat(stubApp.labelOf(null, "order_status", "NEW")).isEqualTo("新建（修订）");
    }

    /** 桩应用：本地缓存 + 轮询 outbox 消费变更事件后失效缓存。 */
    private static final class StubDictionaryClient {

        private final Map<String, String> localCache = new ConcurrentHashMap<>();
        private final DictApi dictApi;
        private final JdbcTemplate jdbc;

        StubDictionaryClient(DictApi dictApi, JdbcTemplate jdbc) {
            this.dictApi = dictApi;
            this.jdbc = jdbc;
        }

        String labelOf(String appCode, String typeCode, String itemCode) {
            return localCache.computeIfAbsent(typeCode + "|" + itemCode, key -> dictApi.items(appCode, typeCode)
                    .stream()
                    .filter(item -> item.code().equals(itemCode))
                    .map(DictItemView::label)
                    .findFirst()
                    .orElse(null));
        }

        /** 消费待处理事件；命中 {@code fdn.dict.changed} 则整体失效本地缓存。 */
        int consumePendingEvents() {
            List<Map<String, Object>> events = jdbc.queryForList(
                    "SELECT event_type FROM fdn_outbox WHERE status = 'NEW' ORDER BY id");
            for (Map<String, Object> event : events) {
                if (CommonEventTypes.DICT_CHANGED.equals(event.get("event_type"))) {
                    localCache.clear();
                }
            }
            return events.size();
        }
    }
}
