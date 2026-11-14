package com.foundation.sync.adapter.ldap;

import com.foundation.sync.spi.ExternalIdentityHint;
import com.foundation.sync.spi.ExternalRecord;
import com.foundation.sync.spi.IncrementalCursor;
import com.foundation.sync.spi.IncrementalMode;
import com.foundation.sync.spi.MatchBy;
import com.foundation.sync.spi.Page;
import com.foundation.sync.spi.PageCursor;
import com.foundation.sync.spi.ProbeResult;
import com.foundation.sync.spi.SourceCapabilities;
import com.foundation.sync.spi.SyncCredentialsRef;
import com.foundation.sync.spi.SyncObjectType;
import com.foundation.sync.spi.SyncSourceAdapter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * LDAP / AD 同步适配器（F-08 BAS-12）：实现 BAS-11 的 {@link SyncSourceAdapter}。
 *
 * <p>组织单元与人员两类对象；增量依 {@code incrementalMode}（AUTO / CHANGE_NUMBER / TIMESTAMP）
 * 结合 {@code adMode} 决定：AD 用 {@code uSNChanged} / {@code whenChanged}，OpenLDAP 用
 * {@code modifyTimestamp}。外部 ID 取 objectGUID（十六进制）/ entryUUID。
 *
 * <p>Pagination、限流与退避重试由 {@link LdapApiClient} 实现内部完成，本类只做映射与过滤编排。
 */
public class LdapSyncAdapter implements SyncSourceAdapter {

    /** 来源标识。 */
    public static final String SOURCE_TYPE = "LDAP";

    private static final String CHANGE_NUMBER_FIELD = "change_number";
    private static final DateTimeFormatter LDAP_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'").withZone(ZoneOffset.UTC);

    private final LdapProperties properties;
    private final LdapApiClient client;

    public LdapSyncAdapter(LdapProperties properties, LdapApiClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public String sourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public SourceCapabilities capabilities() {
        EnumSet<SyncObjectType> objects = EnumSet.of(SyncObjectType.ORG_UNIT, SyncObjectType.PERSON);
        EnumSet<IncrementalMode> modes = EnumSet.of(effectiveMode());
        return new SourceCapabilities(objects, modes, true, true);
    }

    @Override
    public void authenticate(SyncCredentialsRef credentials) {
        client.authenticate(credentials);
    }

    @Override
    public ProbeResult probe() {
        List<ProbeResult.ProbeItem> items = new ArrayList<>();

        boolean reachable;
        try {
            reachable = client.ping();
        } catch (RuntimeException e) {
            items.add(ProbeResult.ProbeItem.fail("bind", messageOf(e)));
            return ProbeResult.failed(items);
        }
        if (!reachable) {
            items.add(ProbeResult.ProbeItem.fail("bind", "LDAP 绑定失败或不可达"));
            return ProbeResult.failed(items);
        }
        items.add(ProbeResult.ProbeItem.pass("bind"));

        String baseDn = firstBaseDn();
        items.add(searchOnce("search-org-unit", baseDn, properties.getOrgFilter()));
        items.add(searchOnce("search-person", baseDn, properties.getUserFilter()));

        boolean allOk = items.stream().allMatch(ProbeResult.ProbeItem::ok);
        return allOk ? ProbeResult.ok(items) : ProbeResult.failed(items);
    }

    @Override
    public Page<ExternalRecord> fetchFull(SyncObjectType objectType, PageCursor cursor) {
        if (objectType != SyncObjectType.ORG_UNIT && objectType != SyncObjectType.PERSON) {
            return Page.empty();
        }
        String filter = filterFor(objectType);
        List<ExternalRecord> records = new ArrayList<>();
        for (String baseDn : properties.getBaseDns()) {
            for (LdapEntry entry : client.entries(baseDn, filter)) {
                records.add(map(objectType, entry));
            }
        }
        return Page.last(records);
    }

    @Override
    public Page<ExternalRecord> fetchIncremental(SyncObjectType objectType, IncrementalCursor cursor) {
        if (objectType != SyncObjectType.ORG_UNIT && objectType != SyncObjectType.PERSON) {
            return Page.empty();
        }
        if (cursor == null || !cursor.hasValue()) {
            return Page.empty();
        }
        IncrementalMode mode = cursor.mode();
        if (mode == null || !mode.supportsIncremental()
                || mode == IncrementalMode.EVENT_CALLBACK) {
            return Page.empty();
        }

        String filter = combine(filterFor(objectType), incrementalCondition(mode, cursor.value()));
        List<ExternalRecord> records = new ArrayList<>();
        for (String baseDn : properties.getBaseDns()) {
            for (LdapEntry entry : client.entries(baseDn, filter)) {
                records.add(map(objectType, entry));
            }
        }
        return Page.last(records);
    }

    @Override
    public Optional<ExternalRecord> fetchOne(SyncObjectType objectType, String externalId) {
        if (objectType != SyncObjectType.ORG_UNIT && objectType != SyncObjectType.PERSON) {
            return Optional.empty();
        }
        return client.entry(externalId).map(entry -> map(objectType, entry));
    }

    @Override
    public IncrementalCursor advanceCursor(IncrementalCursor cursor, Page<ExternalRecord> page) {
        if (cursor == null || page == null || page.isEmpty()) {
            return cursor;
        }
        if (cursor.mode() == IncrementalMode.CHANGE_NUMBER) {
            String max = null;
            for (ExternalRecord record : page.items()) {
                Object value = record.fields().get(CHANGE_NUMBER_FIELD);
                if (value == null) {
                    continue;
                }
                String current = String.valueOf(value);
                if (max == null || compareChangeNumber(current, max) > 0) {
                    max = current;
                }
            }
            return max == null ? cursor : cursor.advanced(max, cursor.timestamp());
        }
        // TIMESTAMP（默认兜底）
        Instant max = null;
        for (ExternalRecord record : page.items()) {
            Instant at = record.updatedAt();
            if (at != null && (max == null || at.isAfter(max))) {
                max = at;
            }
        }
        if (max == null) {
            return cursor;
        }
        return cursor.advanced(LDAP_TIME.format(max), max);
    }

    @Override
    public Optional<ExternalIdentityHint> resolveExternalIdentity(ExternalRecord record) {
        if (record == null || record.objectType() != SyncObjectType.PERSON) {
            return Optional.empty();
        }
        Map<String, Object> fields = record.fields();
        String empNo = asString(fields.get("emp_no"));
        if (empNo != null) {
            return Optional.of(new ExternalIdentityHint(SOURCE_TYPE, record.externalId(), MatchBy.EMP_NO, empNo));
        }
        String email = asString(fields.get("email"));
        if (email != null) {
            return Optional.of(new ExternalIdentityHint(SOURCE_TYPE, record.externalId(),
                    MatchBy.VERIFIED_EMAIL, email));
        }
        return Optional.empty();
    }

    // ---------- 内部 ----------

    /** 增量方式：AUTO 时依 adMode 决定（AD → CHANGE_NUMBER，OpenLDAP → TIMESTAMP）。 */
    private IncrementalMode effectiveMode() {
        String configured = properties.getIncrementalMode();
        if (configured != null) {
            IncrementalMode mode = IncrementalMode.of(configured);
            if (mode == IncrementalMode.CHANGE_NUMBER || mode == IncrementalMode.TIMESTAMP) {
                return mode;
            }
        }
        return properties.isAdMode() ? IncrementalMode.CHANGE_NUMBER : IncrementalMode.TIMESTAMP;
    }

    private String filterFor(SyncObjectType objectType) {
        return objectType == SyncObjectType.ORG_UNIT ? properties.getOrgFilter() : properties.getUserFilter();
    }

    private String firstBaseDn() {
        return properties.getBaseDns().isEmpty() ? "" : properties.getBaseDns().get(0);
    }

    private ProbeResult.ProbeItem searchOnce(String name, String baseDn, String filter) {
        try {
            client.entries(baseDn, filter);
            return ProbeResult.ProbeItem.pass(name);
        } catch (RuntimeException e) {
            return ProbeResult.ProbeItem.fail(name, messageOf(e));
        }
    }

    private ExternalRecord map(SyncObjectType objectType, LdapEntry entry) {
        ExternalRecord base = objectType == SyncObjectType.ORG_UNIT
                ? entry.toOrgRecord() : entry.toPersonRecord();
        Map<String, Object> fields = new LinkedHashMap<>(base.fields());
        if (entry.changeNumber() != null) {
            fields.put(CHANGE_NUMBER_FIELD, entry.changeNumber());
        }
        Instant updatedAt = base.updatedAt() != null ? base.updatedAt() : entry.changedAt();
        return new ExternalRecord(base.objectType(), base.externalId(), fields,
                base.parentExternalId(), base.deleted(), updatedAt);
    }

    /** 增量过滤条件：CHANGE_NUMBER 用 {@code uSNChanged}；TIMESTAMP 依 adMode 用 whenChanged / modifyTimestamp。 */
    private String incrementalCondition(IncrementalMode mode, String value) {
        if (mode == IncrementalMode.CHANGE_NUMBER) {
            long next = 0L;
            try {
                next = Long.parseLong(value.trim()) + 1L;
            } catch (NumberFormatException e) {
                return "(uSNChanged>=" + escape(value.trim()) + ")";
            }
            return "(uSNChanged>=" + next + ")";
        }
        String attribute = properties.isAdMode() ? "whenChanged" : "modifyTimestamp";
        return "(" + attribute + ">=" + escape(value.trim()) + ")";
    }

    private static String combine(String objectClassFilter, String condition) {
        String base = objectClassFilter == null || objectClassFilter.isBlank()
                ? "(objectClass=*)" : objectClassFilter.trim();
        return "(&" + base + condition + ")";
    }

    private static int compareChangeNumber(String a, String b) {
        try {
            return Long.compare(Long.parseLong(a.trim()), Long.parseLong(b.trim()));
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    }

    private static String escape(String value) {
        return value.substring(0, Math.min(value.length(), 512));
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String s = value instanceof String str ? str : String.valueOf(value);
        return s.isBlank() ? null : s.trim();
    }

    private static String messageOf(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
