package com.foundation.sync.scim.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.sync.domain.DeleteProtectionVerdict;
import com.foundation.sync.domain.LandedRecord;
import com.foundation.sync.domain.MasterDataPort;
import com.foundation.sync.scim.domain.ScimConstants;
import com.foundation.sync.scim.domain.ScimDeleteProtection;
import com.foundation.sync.scim.domain.ScimFilter;
import com.foundation.sync.scim.domain.ScimGroup;
import com.foundation.sync.scim.domain.ScimGroupMapping;
import com.foundation.sync.scim.domain.ScimOperations;
import com.foundation.sync.scim.domain.ScimPatch;
import com.foundation.sync.scim.domain.ScimValues;
import com.foundation.sync.spi.SyncKey;
import com.foundation.sync.spi.SyncObjectType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SCIM Groups 应用服务（F-08 BAS-13 §1、§3、§4）：{@code /scim/v2/Groups} 的增删改查。
 *
 * <p>SCIM Group 映射到**有层级的** {@code fdn_org_unit}（部门作为组）：{@code parentExternalId} 决定
 * 建树位置，深度由落地侧推导并受 {@code ck_org_unit_depth}（1〜10）约束；停用受窗口累计差异保护。
 */
public class ScimGroupService {

    private final MasterDataPort masterData;
    private final ScimResourceWriter writer;
    private final ScimDeleteProtection protection;
    private final ScimIdempotencyStore idempotency;

    public ScimGroupService(MasterDataPort masterData,
                            ScimResourceWriter writer,
                            ScimDeleteProtection protection,
                            ScimIdempotencyStore idempotency) {
        this.masterData = masterData;
        this.writer = writer;
        this.protection = protection;
        this.idempotency = idempotency;
    }

    /** 过滤 + 分页 + 排序。 */
    public Map<String, Object> list(String filter, Integer startIndex, Integer count, String sortBy,
                                    String sortOrder) {
        ScimFilter parsed = ScimFilter.parse(filter, ScimConstants.GROUP_FILTER_ATTRIBUTES);
        List<LandedRecord> records = new ArrayList<>(masterData.listBySource(SyncObjectType.ORG_UNIT,
                ScimConstants.SOURCE));
        records.removeIf(record -> !matches(parsed, record));
        sort(records, sortBy, sortOrder);
        int total = records.size();
        int from = Math.max(0, (startIndex == null ? 1 : startIndex) - 1);
        int size = count == null ? total : Math.max(0, count);
        List<LandedRecord> page = from >= total ? List.of()
                : records.subList(from, Math.min(total, from + size));
        return ScimRepresentation.list(page.stream().map(ScimRepresentation::group).toList(),
                startIndex == null ? 1 : startIndex, total);
    }

    /** 按资源 ID（本地主键）取组。 */
    public Map<String, Object> get(String id) {
        return ScimRepresentation.group(require(id));
    }

    /** 新增；同一 {@code externalId} 重复抵达返回 {@code 409}。 */
    public Map<String, Object> create(Map<String, Object> payload, String idempotencyKey) {
        Optional<Object> cached = idempotency.get(idempotencyKey);
        if (cached.isPresent()) {
            return asMap(cached.get());
        }
        ScimGroup group = ScimGroup.from(payload);
        requireExternalId(group.externalId());
        if (masterData.find(SyncObjectType.ORG_UNIT, SyncKey.of(ScimConstants.SOURCE, group.externalId()))
                .isPresent()) {
            throw new BizException(ErrorCode.CONFLICT, "externalId 已存在：" + group.externalId());
        }
        LandedRecord landed = applyGroup(group, null);
        Map<String, Object> view = ScimRepresentation.group(landed);
        idempotency.put(idempotencyKey, view);
        return view;
    }

    /** 整包替换（{@code PUT}）。 */
    public Map<String, Object> replace(String id, Map<String, Object> payload) {
        LandedRecord existing = require(id);
        ScimGroup parsed = ScimGroup.from(payload);
        ScimGroup group = parsed.externalId() == null
                ? new ScimGroup(existing.key().externalId(), parsed.displayName(), parsed.code(), parsed.type(),
                parsed.active(), parsed.parentExternalId())
                : parsed;
        return ScimRepresentation.group(applyGroup(group, existing));
    }

    /** 局部更新（{@code PATCH}，RFC 7644 §3.5.2）。 */
    public Map<String, Object> patch(String id, Map<String, Object> body) {
        LandedRecord existing = require(id);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("externalId", existing.key().externalId());
        ScimValues.put(payload, "displayName", existing.fields().get("name"));
        ScimValues.put(payload, "code", existing.fields().get("code"));
        if (existing.parentExternalId() != null) {
            payload.put("parent", existing.parentExternalId());
        }
        payload.put("active", !existing.disabled());
        ScimPatch.apply(payload, ScimOperations.of(body));
        return replace(id, payload);
    }

    /** 删除（实为停用）；受窗口累计差异保护。 */
    public void delete(String id) {
        LandedRecord existing = require(id);
        if (existing.disabled()) {
            return;
        }
        DeleteProtectionVerdict verdict = protection.accumulate(SyncObjectType.ORG_UNIT, 1, 0);
        if (verdict.pause()) {
            throw new BizException(ErrorCode.CONFLICT,
                    "停用累计超阈值，已暂停待管理员确认（窗口累计）");
        }
        writer.disable(SyncObjectType.ORG_UNIT, existing.key().externalId());
    }

    // ------------------------------------------------------------------ 内部

    private LandedRecord applyGroup(ScimGroup group, LandedRecord existing) {
        if (!group.active() && existing != null && !existing.disabled()) {
            DeleteProtectionVerdict verdict = protection.accumulate(SyncObjectType.ORG_UNIT, 1, 0);
            if (verdict.pause()) {
                throw new BizException(ErrorCode.CONFLICT,
                        "停用累计超阈值，已暂停待管理员确认（窗口累计）");
            }
            return writer.disable(SyncObjectType.ORG_UNIT, group.externalId());
        }
        return writer.apply(ScimGroupMapping.toMappedRecord(group));
    }

    private LandedRecord require(String id) {
        return masterData.listBySource(SyncObjectType.ORG_UNIT, ScimConstants.SOURCE).stream()
                .filter(record -> record.localId().equals(id))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "组不存在：" + id));
    }

    private static boolean matches(ScimFilter filter, LandedRecord record) {
        if (!filter.present()) {
            return true;
        }
        String value = switch (filter.attribute()) {
            case "displayName" -> ScimValues.text(record.fields().get("name"));
            default -> record.key().externalId();
        };
        return filter.matches(value);
    }

    private static void sort(List<LandedRecord> records, String sortBy, String sortOrder) {
        if (sortBy == null || sortBy.isBlank()) {
            return;
        }
        if (!ScimConstants.GROUP_SORT_ATTRIBUTES.contains(sortBy)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不支持的排序属性：" + sortBy);
        }
        Comparator<LandedRecord> comparator = Comparator.comparing(
                record -> "displayName".equals(sortBy) ? ScimValues.text(record.fields().get("name"))
                        : record.key().externalId(),
                Comparator.nullsLast(String::compareToIgnoreCase));
        if ("descending".equalsIgnoreCase(ScimValues.text(sortOrder))) {
            comparator = comparator.reversed();
        }
        records.sort(comparator);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static void requireExternalId(String externalId) {
        if (externalId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "SCIM Group 缺少 externalId（幂等键）");
        }
    }
}
