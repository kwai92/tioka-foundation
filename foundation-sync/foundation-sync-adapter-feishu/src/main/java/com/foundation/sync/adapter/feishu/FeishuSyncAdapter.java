package com.foundation.sync.adapter.feishu;

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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * 飞书通讯录同步适配器（F-08 BAS-12）：实现 BAS-11 的 {@link SyncSourceAdapter} SPI。
 *
 * <p>以插件方式注册；增量依赖事件回调（{@code EVENT_CALLBACK}），
 * 事件经 {@link #enqueueEvent(FeishuEvent)} 入队后由 {@link #fetchIncremental} 回查落库，
 * 定时全量作为兜底。
 */
public class FeishuSyncAdapter implements SyncSourceAdapter {

    /** 来源标识。 */
    public static final String SOURCE_TYPE = "FEISHU";

    private final FeishuProperties properties;
    private final FeishuApiClient client;
    private final Deque<FeishuEvent> pendingEvents = new ArrayDeque<>();

    public FeishuSyncAdapter(FeishuProperties properties, FeishuApiClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public String sourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public SourceCapabilities capabilities() {
        return new SourceCapabilities(
                EnumSet.of(SyncObjectType.ORG_UNIT, SyncObjectType.PERSON, SyncObjectType.POSITION),
                EnumSet.of(IncrementalMode.EVENT_CALLBACK),
                true,
                true);
    }

    @Override
    public void authenticate(SyncCredentialsRef credentials) {
        client.authenticate(credentials);
    }

    @Override
    public ProbeResult probe() {
        List<ProbeResult.ProbeItem> items = new ArrayList<>();

        try {
            if (client.ping()) {
                items.add(ProbeResult.ProbeItem.pass("auth"));
            } else {
                items.add(ProbeResult.ProbeItem.fail("auth", "认证或连通性探测失败"));
            }
        } catch (RuntimeException ex) {
            items.add(ProbeResult.ProbeItem.fail("auth", message(ex)));
        }

        try {
            client.departments(PageCursor.first());
            items.add(ProbeResult.ProbeItem.pass("fetch-org-unit"));
        } catch (RuntimeException ex) {
            items.add(ProbeResult.ProbeItem.fail("fetch-org-unit", message(ex)));
        }

        try {
            client.users(PageCursor.first());
            items.add(ProbeResult.ProbeItem.pass("fetch-user"));
        } catch (RuntimeException ex) {
            items.add(ProbeResult.ProbeItem.fail("fetch-user", message(ex)));
        }

        boolean allOk = items.stream().allMatch(ProbeResult.ProbeItem::ok);
        return allOk ? ProbeResult.ok(items) : ProbeResult.failed(items);
    }

    @Override
    public Page<ExternalRecord> fetchFull(SyncObjectType objectType, PageCursor cursor) {
        PageCursor effective = cursor == null ? PageCursor.first() : cursor;
        if (objectType == SyncObjectType.ORG_UNIT) {
            return mapDepartments(client.departments(effective));
        }
        if (objectType == SyncObjectType.PERSON || objectType == SyncObjectType.POSITION) {
            return mapUsers(client.users(effective));
        }
        return Page.empty();
    }

    @Override
    public Page<ExternalRecord> fetchIncremental(SyncObjectType objectType, IncrementalCursor cursor) {
        if (objectType == null) {
            return Page.empty();
        }
        List<ExternalRecord> records = new ArrayList<>();
        FeishuEvent event;
        while ((event = pollMatching(objectType)) != null) {
            for (String externalId : event.externalIds()) {
                fetchOne(objectType, externalId).ifPresent(records::add);
            }
        }
        return records.isEmpty() ? Page.empty() : Page.last(records);
    }

    @Override
    public Optional<ExternalRecord> fetchOne(SyncObjectType objectType, String externalId) {
        if (objectType == null || externalId == null || externalId.isBlank()) {
            return Optional.empty();
        }
        if (objectType == SyncObjectType.ORG_UNIT) {
            return client.department(externalId).map(FeishuDepartment::toExternalRecord);
        }
        return client.user(externalId).map(FeishuUser::toExternalRecord);
    }

    @Override
    public IncrementalCursor advanceCursor(IncrementalCursor cursor, Page<ExternalRecord> page) {
        IncrementalCursor base = cursor == null
                ? IncrementalCursor.initial(IncrementalMode.EVENT_CALLBACK)
                : cursor;
        Instant now = Instant.now();
        return base.advanced(now.toString(), now);
    }

    @Override
    public Optional<ExternalIdentityHint> resolveExternalIdentity(ExternalRecord record) {
        if (record == null || record.objectType() != SyncObjectType.PERSON) {
            return Optional.empty();
        }
        Object employeeNo = record.fields().get("emp_no");
        if (hasText(employeeNo)) {
            return Optional.of(new ExternalIdentityHint(SOURCE_TYPE, record.externalId(),
                    MatchBy.EMP_NO, String.valueOf(employeeNo).trim()));
        }
        Object email = record.fields().get("email");
        if (hasText(email)) {
            return Optional.of(new ExternalIdentityHint(SOURCE_TYPE, record.externalId(),
                    MatchBy.VERIFIED_EMAIL, String.valueOf(email).trim()));
        }
        return Optional.empty();
    }

    /** 事件回调入队（由回调入口调用）。 */
    public void enqueueEvent(FeishuEvent event) {
        if (event == null) {
            return;
        }
        synchronized (pendingEvents) {
            pendingEvents.addLast(event);
        }
    }

    /** 待处理事件数量（观测/测试用）。 */
    public int pendingEventCount() {
        synchronized (pendingEvents) {
            return pendingEvents.size();
        }
    }

    private FeishuEvent pollMatching(SyncObjectType objectType) {
        synchronized (pendingEvents) {
            Iterator<FeishuEvent> iterator = pendingEvents.iterator();
            while (iterator.hasNext()) {
                FeishuEvent next = iterator.next();
                if (next.objectType() == objectType) {
                    iterator.remove();
                    return next;
                }
            }
            return null;
        }
    }

    private Page<ExternalRecord> mapDepartments(Page<FeishuDepartment> page) {
        List<ExternalRecord> records = page.items().stream()
                .map(FeishuDepartment::toExternalRecord)
                .toList();
        return new Page<>(records, page.next(), page.hasMore());
    }

    private Page<ExternalRecord> mapUsers(Page<FeishuUser> page) {
        List<ExternalRecord> records = page.items().stream()
                .map(FeishuUser::toExternalRecord)
                .toList();
        return new Page<>(records, page.next(), page.hasMore());
    }

    private static boolean hasText(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }

    private static String message(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}
