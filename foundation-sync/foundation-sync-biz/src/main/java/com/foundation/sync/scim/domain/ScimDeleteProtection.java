package com.foundation.sync.scim.domain;

import com.foundation.sync.api.dto.DiffKind;
import com.foundation.sync.domain.DeleteProtectionPolicy;
import com.foundation.sync.domain.DeleteProtectionVerdict;
import com.foundation.sync.domain.DiffEntry;
import com.foundation.sync.domain.DiffResult;
import com.foundation.sync.spi.SyncObjectType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * SCIM 实时推送的差异保护（F-08 BAS-13 §4）。
 *
 * <p>SCIM 无预演（实时推送）：把 BAS-11 {@link DeleteProtectionPolicy} 的「一次快照」判定改为
 * **时间窗口累计**——窗口（缺省 10 分钟）内停用累计超过阈值（5% 或 50 人／组织单元 10 个）即
 * 判定暂停剩余停用；判定逻辑仍复用 {@link DeleteProtectionPolicy#evaluate}（以累计计数构造合成差异）。
 *
 * <p>新增与修改不经过本类，照常落地。
 */
public class ScimDeleteProtection {

    private final DeleteProtectionPolicy policy;
    private final Duration window;
    private final Supplier<Instant> clock;
    private final Deque<Sample> samples = new ArrayDeque<>();

    private record Sample(SyncObjectType objectType, int count, Instant at) {
    }

    public ScimDeleteProtection(DeleteProtectionPolicy policy, Duration window) {
        this(policy, window, Instant::now);
    }

    public ScimDeleteProtection(DeleteProtectionPolicy policy, Duration window, Supplier<Instant> clock) {
        this.policy = policy;
        this.window = window;
        this.clock = clock;
    }

    /**
     * 记入一次停用并判定窗口累计是否超阈值。
     *
     * @param objectType    对象类型（{@code PERSON} / {@code ORG_UNIT}）
     * @param disabled      本次停用条数
     * @param totalPersons  现存人员总数（百分比基数）
     */
    public synchronized DeleteProtectionVerdict accumulate(SyncObjectType objectType, int disabled,
                                                           int totalPersons) {
        Instant now = clock.get();
        evict(now);
        if (disabled > 0) {
            samples.addLast(new Sample(objectType, disabled, now));
        }
        return policy.evaluate(synthetic(accumulated(SyncObjectType.PERSON), accumulated(SyncObjectType.ORG_UNIT)),
                totalPersons);
    }

    /** 窗口内某对象类型的累计停用数。 */
    public synchronized int accumulated(SyncObjectType objectType) {
        int total = 0;
        for (Sample sample : samples) {
            if (sample.objectType() == objectType) {
                total += sample.count();
            }
        }
        return total;
    }

    /** 管理员确认后清空窗口累计。 */
    public synchronized void reset() {
        samples.clear();
    }

    private void evict(Instant now) {
        Instant cutoff = now.minus(window);
        while (!samples.isEmpty() && !samples.peekFirst().at().isAfter(cutoff)) {
            samples.pollFirst();
        }
    }

    /** 以累计计数构造合成差异，复用 BAS-11 的阈值判定口径。 */
    private static DiffResult synthetic(int persons, int orgUnits) {
        List<DiffEntry> entries = new ArrayList<>();
        for (int i = 0; i < persons; i++) {
            entries.add(disable(SyncObjectType.PERSON));
        }
        for (int i = 0; i < orgUnits; i++) {
            entries.add(disable(SyncObjectType.ORG_UNIT));
        }
        return DiffResult.of(entries);
    }

    private static DiffEntry disable(SyncObjectType objectType) {
        return new DiffEntry(objectType, null, DiffKind.DISABLE, Map.of(), Set.of(), Set.of(), null);
    }
}
