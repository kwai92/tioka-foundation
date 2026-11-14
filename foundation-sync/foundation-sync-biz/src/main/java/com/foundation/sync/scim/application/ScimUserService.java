package com.foundation.sync.scim.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.sync.domain.DeleteProtectionVerdict;
import com.foundation.sync.domain.LandedRecord;
import com.foundation.sync.domain.MasterDataPort;
import com.foundation.sync.scim.domain.ScimConstants;
import com.foundation.sync.scim.domain.ScimDeleteProtection;
import com.foundation.sync.scim.domain.ScimExternalIdentityResolver;
import com.foundation.sync.scim.domain.ScimFilter;
import com.foundation.sync.scim.domain.ScimOperations;
import com.foundation.sync.scim.domain.ScimPersonLookup;
import com.foundation.sync.scim.domain.ScimPatch;
import com.foundation.sync.scim.domain.ScimUser;
import com.foundation.sync.scim.domain.ScimUserMapping;
import com.foundation.sync.scim.domain.ScimValues;
import com.foundation.sync.spi.SyncKey;
import com.foundation.sync.spi.SyncObjectType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SCIM Users 应用服务（F-08 BAS-13 §1、§3、§4）：{@code /scim/v2/Users} 的增删改查。
 *
 * <p>落地经 {@link ScimResourceWriter}（复用 BAS-11 差异算法与本地锁定）；{@code externalId} 为幂等键，
 * 重复 {@code POST} 返回 {@code 409}，{@code Idempotency-Key} 命中缓存直接返回首次结果。
 * 停用受窗口累计差异保护；外部身份仅按工号 / 已验证邮箱关联（{@link ScimExternalIdentityResolver}），
 * 关联不上留待处理关联、**不自动建人**。
 */
public class ScimUserService {

    private final MasterDataPort masterData;
    private final ScimResourceWriter writer;
    private final ScimExternalIdentityResolver resolver;
    private final ScimPersonLookup lookup;
    private final ScimDeleteProtection protection;
    private final ScimIdempotencyStore idempotency;

    public ScimUserService(MasterDataPort masterData,
                           ScimResourceWriter writer,
                           ScimExternalIdentityResolver resolver,
                           ScimPersonLookup lookup,
                           ScimDeleteProtection protection,
                           ScimIdempotencyStore idempotency) {
        this.masterData = masterData;
        this.writer = writer;
        this.resolver = resolver;
        this.lookup = lookup;
        this.protection = protection;
        this.idempotency = idempotency;
    }

    /** 过滤 + 分页 + 排序。 */
    public Map<String, Object> list(String filter, Integer startIndex, Integer count, String sortBy,
                                    String sortOrder) {
        ScimFilter parsed = ScimFilter.parse(filter, ScimConstants.USER_FILTER_ATTRIBUTES);
        List<LandedRecord> records = new ArrayList<>(masterData.listBySource(SyncObjectType.PERSON,
                ScimConstants.SOURCE));
        records.removeIf(record -> !matches(parsed, record));
        sort(records, sortBy, sortOrder);
        int total = records.size();
        int from = Math.max(0, (startIndex == null ? 1 : startIndex) - 1);
        int size = count == null ? total : Math.max(0, count);
        List<LandedRecord> page = from >= total ? List.of()
                : records.subList(from, Math.min(total, from + size));
        return ScimRepresentation.list(page.stream().map(ScimRepresentation::user).toList(),
                startIndex == null ? 1 : startIndex, total);
    }

    /** 按资源 ID（本地主键）取用户。 */
    public Map<String, Object> get(String id) {
        return ScimRepresentation.user(require(id));
    }

    /** 新增；同一 {@code externalId} 重复抵达返回 {@code 409}。 */
    public Map<String, Object> create(Map<String, Object> payload, String idempotencyKey) {
        Optional<Object> cached = idempotency.get(idempotencyKey);
        if (cached.isPresent()) {
            return asMap(cached.get());
        }
        ScimUser user = ScimUser.from(payload);
        requireExternalId(user.externalId());
        if (masterData.find(SyncObjectType.PERSON, SyncKey.of(ScimConstants.SOURCE, user.externalId())).isPresent()) {
            throw new BizException(ErrorCode.CONFLICT, "externalId 已存在：" + user.externalId());
        }
        LandedRecord landed = applyUser(user, null);
        Map<String, Object> view = ScimRepresentation.user(landed);
        idempotency.put(idempotencyKey, view);
        return view;
    }

    /** 整包替换（{@code PUT}）。 */
    public Map<String, Object> replace(String id, Map<String, Object> payload) {
        LandedRecord existing = require(id);
        ScimUser parsed = ScimUser.from(payload);
        ScimUser user = parsed.externalId() == null
                ? new ScimUser(existing.key().externalId(), parsed.userName(), parsed.displayName(),
                parsed.employeeNumber(), parsed.email(), parsed.mobile(), parsed.active())
                : parsed;
        return ScimRepresentation.user(applyUser(user, existing));
    }

    /** 局部更新（{@code PATCH}，RFC 7644 §3.5.2）。 */
    public Map<String, Object> patch(String id, Map<String, Object> body) {
        LandedRecord existing = require(id);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("externalId", existing.key().externalId());
        ScimValues.put(payload, "userName", existing.fields().get("empNo"));
        ScimValues.put(payload, "displayName", existing.fields().get("name"));
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
        guardDisable(SyncObjectType.PERSON);
        writer.disable(SyncObjectType.PERSON, existing.key().externalId());
    }

    // ------------------------------------------------------------------ 内部

    private LandedRecord applyUser(ScimUser user, LandedRecord existing) {
        if (!user.active() && existing != null && !existing.disabled()) {
            guardDisable(SyncObjectType.PERSON);
            return writer.disable(SyncObjectType.PERSON, user.externalId());
        }
        LandedRecord landed = writer.apply(ScimUserMapping.toMappedRecord(user));
        linkExternalIdentity(user);
        return landed;
    }

    /** 窗口累计超阈值即拒绝本次停用（新增与修改照常）。 */
    private void guardDisable(SyncObjectType objectType) {
        DeleteProtectionVerdict verdict = protection.accumulate(objectType, 1, activePersonCount());
        if (verdict.pause()) {
            throw new BizException(ErrorCode.CONFLICT,
                    "停用累计超阈值，已暂停待管理员确认（窗口累计）");
        }
    }

    /** 按工号 / 已验证邮箱关联外部身份；关联不上留待处理关联，不自动建人。 */
    private void linkExternalIdentity(ScimUser user) {
        if (user.externalId() == null) {
            return;
        }
        Optional<ScimExternalIdentityResolver.Resolution> resolution = resolver.resolve(user, lookup);
        if (resolution.isEmpty()) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("personId", resolution.get().personId());
        fields.put("linkMode", ScimConstants.LINK_MODE_AUTO);
        fields.put("linkedAt", Instant.now().toString());
        SyncKey key = SyncKey.of(ScimConstants.EXTERNAL_IDENTITY_SOURCE, user.externalId());
        masterData.find(SyncObjectType.EXTERNAL_IDENTITY, key).ifPresentOrElse(
                existing -> masterData.update(SyncObjectType.EXTERNAL_IDENTITY, existing, fields),
                () -> masterData.create(SyncObjectType.EXTERNAL_IDENTITY, key, fields, null));
    }

    private int activePersonCount() {
        int total = 0;
        for (LandedRecord record : masterData.listBySource(SyncObjectType.PERSON, ScimConstants.SOURCE)) {
            if (!record.disabled()) {
                total++;
            }
        }
        return total;
    }

    private LandedRecord require(String id) {
        return masterData.listBySource(SyncObjectType.PERSON, ScimConstants.SOURCE).stream()
                .filter(record -> record.localId().equals(id))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "用户不存在：" + id));
    }

    private static boolean matches(ScimFilter filter, LandedRecord record) {
        if (!filter.present()) {
            return true;
        }
        String value = switch (filter.attribute()) {
            case "userName" -> ScimValues.text(record.fields().get("empNo"));
            case "displayName" -> ScimValues.text(record.fields().get("name"));
            case "externalId" -> record.key().externalId();
            default -> null;
        };
        return filter.matches(value);
    }

    private static void sort(List<LandedRecord> records, String sortBy, String sortOrder) {
        if (sortBy == null || sortBy.isBlank()) {
            return;
        }
        if (!ScimConstants.USER_SORT_ATTRIBUTES.contains(sortBy)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不支持的排序属性：" + sortBy);
        }
        Comparator<LandedRecord> comparator = Comparator.comparing(
                record -> sortKey(sortBy, record), Comparator.nullsLast(String::compareToIgnoreCase));
        if ("descending".equalsIgnoreCase(ScimValues.text(sortOrder))) {
            comparator = comparator.reversed();
        }
        records.sort(comparator);
    }

    private static String sortKey(String sortBy, LandedRecord record) {
        return switch (sortBy) {
            case "userName" -> ScimValues.text(record.fields().get("empNo"));
            case "displayName" -> ScimValues.text(record.fields().get("name"));
            default -> record.key().externalId();
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static void requireExternalId(String externalId) {
        if (externalId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "SCIM User 缺少 externalId（幂等键）");
        }
    }
}
