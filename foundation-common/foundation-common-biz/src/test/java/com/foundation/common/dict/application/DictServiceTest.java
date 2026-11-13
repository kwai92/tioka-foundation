package com.foundation.common.dict.application;

import com.foundation.common.api.dict.DictItemCmd;
import com.foundation.common.api.dict.DictItemView;
import com.foundation.common.api.dict.DictTypeCmd;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.api.event.CommonEventTypes;
import com.foundation.common.dict.config.DictProperties;
import com.foundation.common.dict.domain.DictItem;
import com.foundation.common.dict.domain.DictType;
import com.foundation.common.dict.infrastructure.DictItemRepository;
import com.foundation.common.dict.infrastructure.DictTypeRepository;
import com.foundation.common.outbox.EventProperties;
import com.foundation.common.outbox.OutboxAppender;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-02 BAS-09：字典类型/项 CRUD、排序、状态、应用项优先于全局项。 */
class DictServiceTest {

    private final InMemoryDictTypeRepository types = new InMemoryDictTypeRepository();
    private final InMemoryDictItemRepository items = new InMemoryDictItemRepository();
    private final RecordingOutbox outbox = new RecordingOutbox();
    private final DictProperties properties = new DictProperties();

    private DictService service() {
        return new DictService(types, items, outbox, new EventProperties(), properties);
    }

    @Test
    void upsertTypeIsIdempotentByScopeAndCode() {
        DictService service = service();
        service.upsertType(new DictTypeCmd(null, "order_status", "订单状态", null, "alice"));
        String firstId = types.byKey.get("|order_status").id();

        service.upsertType(new DictTypeCmd(null, "order_status", "订单状态（修订）", "ENABLED", "bob"));

        assertThat(types.byKey).hasSize(1);
        assertThat(types.byKey.get("|order_status").id()).isEqualTo(firstId);
        assertThat(types.byKey.get("|order_status").name()).isEqualTo("订单状态（修订）");
        assertThat(types.byKey.get("|order_status").status()).isEqualTo(DictType.ENABLED);
    }

    @Test
    void globalAndAppScopedTypesCoexist() {
        DictService service = service();
        service.upsertType(new DictTypeCmd(null, "order_status", "订单状态", null, "alice"));
        service.upsertType(new DictTypeCmd("tioka", "order_status", "订单状态（Tioka）", null, "alice"));

        assertThat(types.byKey).hasSize(2);
        assertThat(types.byKey.get("tioka|order_status").appCode()).isEqualTo("tioka");
        assertThat(types.byKey.get("|order_status").appCode()).isNull();
    }

    @Test
    void itemsAreSortedAndDisabledItemsAreHidden() {
        DictService service = service();
        service.upsertType(new DictTypeCmd(null, "order_status", "订单状态", null, "alice"));
        service.upsertItem(new DictItemCmd(null, "order_status", "PAID", "已支付", 20, null, "success", null, "alice"));
        service.upsertItem(new DictItemCmd(null, "order_status", "NEW", "新建", 10, null, null, null, "alice"));
        service.upsertItem(new DictItemCmd(null, "order_status", "CANCELLED", "已取消", 30,
                DictType.DISABLED, null, null, "alice"));

        List<DictItemView> views = service.items(null, "order_status");

        assertThat(views).extracting(DictItemView::code).containsExactly("NEW", "PAID");
        assertThat(views.get(0).label()).isEqualTo("新建");
        assertThat(views.get(0).appCode()).isNull();
        assertThat(views.get(0).typeCode()).isEqualTo("order_status");
    }

    @Test
    void appScopedItemsWinOverGlobalOnes() {
        DictService service = service();
        service.upsertType(new DictTypeCmd(null, "order_status", "订单状态", null, "alice"));
        service.upsertType(new DictTypeCmd("tioka", "order_status", "订单状态（Tioka）", null, "alice"));
        service.upsertItem(new DictItemCmd(null, "order_status", "NEW", "全局-新建", 10, null, null, null, "alice"));
        service.upsertItem(new DictItemCmd("tioka", "order_status", "NEW", "Tioka-新建", 10, null, null, null, "alice"));

        assertThat(service.items("tioka", "order_status")).extracting(DictItemView::label)
                .containsExactly("Tioka-新建");
        assertThat(service.items(null, "order_status")).extracting(DictItemView::label)
                .containsExactly("全局-新建");
    }

    @Test
    void upsertItemRequiresExistingType() {
        assertThatThrownBy(() -> service().upsertItem(
                new DictItemCmd(null, "missing", "A", "甲", 1, null, null, null, "alice")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void upsertItemKeepsItemIdAndExtJson() {
        DictService service = service();
        service.upsertType(new DictTypeCmd(null, "order_status", "订单状态", null, "alice"));
        service.upsertItem(new DictItemCmd(null, "order_status", "NEW", "新建", 10, null, null,
                Map.of("color", "blue"), "alice"));
        String firstId = items.byId.values().iterator().next().id();

        service.upsertItem(new DictItemCmd(null, "order_status", "NEW", "新建（修订）", 5, null, "info",
                Map.of("color", "red"), "bob"));

        assertThat(items.byId).hasSize(1);
        DictItem stored = items.byId.get(firstId);
        assertThat(stored.label()).isEqualTo("新建（修订）");
        assertThat(stored.sortNo()).isEqualTo(5);
        assertThat(stored.ext()).containsEntry("color", "red");
    }

    @Test
    void changesAreAppendedToOutboxWithIdVersionAndChangeType() {
        DictService service = service();
        service.upsertType(new DictTypeCmd(null, "order_status", "订单状态", null, "alice"));

        assertThat(outbox.appended).hasSize(1);
        RecordingOutbox.Append append = outbox.appended.get(0);
        assertThat(append.topic()).isEqualTo("fdn-events");
        assertThat(append.eventType()).isEqualTo(CommonEventTypes.DICT_CHANGED);
        assertThat(append.payload()).containsOnlyKeys("id", "version", "changeType");
        assertThat(append.payload().get("changeType")).isEqualTo("UPSERT");
        assertThat(append.payload().get("id")).isEqualTo(append.aggregate());
    }

    @Test
    void writesInvalidateReadCache() {
        properties.setCacheTtl(Duration.ofMinutes(5));
        DictService service = service();
        service.upsertType(new DictTypeCmd(null, "order_status", "订单状态", null, "alice"));
        service.upsertItem(new DictItemCmd(null, "order_status", "NEW", "新建", 10, null, null, null, "alice"));
        assertThat(service.items(null, "order_status")).extracting(DictItemView::label).containsExactly("新建");

        service.upsertItem(new DictItemCmd(null, "order_status", "PAID", "已支付", 20, null, null, null, "alice"));

        assertThat(service.items(null, "order_status")).extracting(DictItemView::label)
                .containsExactly("新建", "已支付");
    }

    private static final class InMemoryDictTypeRepository implements DictTypeRepository {

        private final Map<String, DictType> byKey = new LinkedHashMap<>();

        private static String key(String appCode, String code) {
            return (appCode == null ? "" : appCode) + "|" + code;
        }

        @Override
        public Optional<DictType> find(String appCode, String code) {
            return Optional.ofNullable(byKey.get(key(appCode, code)));
        }

        @Override
        public void insert(DictType type, String operator) {
            byKey.put(key(type.appCode(), type.code()), type);
        }

        @Override
        public void update(DictType type, String operator) {
            byKey.put(key(type.appCode(), type.code()), type);
        }
    }

    private static final class InMemoryDictItemRepository implements DictItemRepository {

        private final Map<String, DictItem> byId = new LinkedHashMap<>();

        @Override
        public List<DictItem> listEnabled(String typeId) {
            return byId.values().stream()
                    .filter(item -> item.typeId().equals(typeId))
                    .filter(item -> DictType.ENABLED.equals(item.status()))
                    .sorted(Comparator.comparingInt(DictItem::sortNo).thenComparing(DictItem::code))
                    .toList();
        }

        @Override
        public Optional<DictItem> find(String typeId, String code) {
            return byId.values().stream()
                    .filter(item -> item.typeId().equals(typeId) && item.code().equals(code))
                    .findFirst();
        }

        @Override
        public void insert(DictItem item) {
            byId.put(item.id(), item);
        }

        @Override
        public void update(DictItem item) {
            byId.put(item.id(), item);
        }
    }

    private static final class RecordingOutbox implements OutboxAppender {

        private final List<Append> appended = new ArrayList<>();

        @Override
        public void append(String topic, String eventType, String aggregate, Map<String, Object> payload) {
            appended.add(new Append(topic, eventType, aggregate, payload));
        }

        private record Append(String topic, String eventType, String aggregate, Map<String, Object> payload) {
        }
    }
}
