package com.foundation.common.dict.application;

import com.foundation.common.api.dict.DictApi;
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
import com.foundation.common.id.CommonIdPrefix;
import com.foundation.common.id.Ids;
import com.foundation.common.api.event.EventProperties;
import com.foundation.common.api.event.OutboxAppender;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 字典应用服务（F-02 BAS-09）：作用域 CRUD + 变更事件。 */
public class DictService implements DictApi {

    private final DictTypeRepository types;
    private final DictItemRepository items;
    private final OutboxAppender outbox;
    private final EventProperties events;
    private final TtlCache<List<DictItemView>> cache;

    public DictService(DictTypeRepository types, DictItemRepository items, OutboxAppender outbox,
                       EventProperties events, DictProperties properties) {
        this.types = types;
        this.items = items;
        this.outbox = outbox;
        this.events = events;
        this.cache = new TtlCache<>(properties.getCacheTtl());
    }

    @Override
    public List<DictItemView> items(String appCode, String typeCode) {
        String scope = normalize(appCode);
        return cache.get(scope + "|" + typeCode, () -> loadItems(scope, typeCode));
    }

    @Override
    @Transactional
    public void upsertType(DictTypeCmd cmd) {
        String scope = normalize(cmd.appCode());
        String code = requireText(cmd.code(), "code");
        String name = requireText(cmd.name(), "name");
        String status = cmd.status() == null ? DictType.ENABLED : cmd.status();
        String operator = operatorOf(cmd.operator());

        Optional<DictType> existing = types.find(scope, code);
        String id = existing.map(DictType::id).orElseGet(() -> Ids.newId(CommonIdPrefix.DICT_TYPE));
        DictType type = new DictType(id, scope, code, name, status);
        existing.ifPresentOrElse(found -> types.update(type, operator), () -> types.insert(type, operator));

        outbox.append(events.getTopic(), CommonEventTypes.DICT_CHANGED, id, changed(id));
        cache.invalidate(scope + "|" + code);
    }

    @Override
    @Transactional
    public void upsertItem(DictItemCmd cmd) {
        String scope = normalize(cmd.appCode());
        String typeCode = requireText(cmd.typeCode(), "typeCode");
        String code = requireText(cmd.code(), "code");
        String label = requireText(cmd.label(), "label");
        String status = cmd.status() == null ? DictType.ENABLED : cmd.status();

        DictType type = types.find(scope, typeCode).orElseThrow(
                () -> new BizException(ErrorCode.NOT_FOUND, "字典类型不存在：" + typeCode));

        Optional<DictItem> existing = items.find(type.id(), code);
        String id = existing.map(DictItem::id).orElseGet(() -> Ids.newId(CommonIdPrefix.DICT_ITEM));
        DictItem item = new DictItem(id, type.id(), code, label, cmd.sortNo(), status, cmd.styleTag(), cmd.ext());
        existing.ifPresentOrElse(found -> items.update(item), () -> items.insert(item));

        outbox.append(events.getTopic(), CommonEventTypes.DICT_CHANGED, id, changed(id));
        cache.invalidate(scope + "|" + typeCode);
    }

    private List<DictItemView> loadItems(String appCode, String typeCode) {
        Optional<DictType> type = appCode == null
                ? types.find(null, typeCode)
                : types.find(appCode, typeCode).or(() -> types.find(null, typeCode));
        if (type.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "字典类型不存在：" + typeCode);
        }
        DictType resolved = type.get();
        return items.listEnabled(resolved.id()).stream()
                .map(item -> new DictItemView(item.id(), resolved.code(), resolved.appCode(), item.code(),
                        item.label(), item.sortNo(), item.status(), item.styleTag(), item.ext()))
                .toList();
    }

    private static Map<String, Object> changed(String id) {
        return Map.of("id", id, "version", 1, "changeType", "UPSERT");
    }

    private static String normalize(String appCode) {
        return appCode == null || appCode.isBlank() ? null : appCode;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, field + " 不能为空");
        }
        return value;
    }

    private static String operatorOf(String operator) {
        return operator == null || operator.isBlank() ? "system" : operator;
    }
}
