package com.foundation.sync.spi;

import java.util.List;

/**
 * 连通性与能力探测结果（F-08 BAS-11 §2）：来源配置保存前调用，逐项返回，不抛异常。
 *
 * @param reachable 是否可达
 * @param items     逐项探测结果
 */
public record ProbeResult(boolean reachable, List<ProbeItem> items) {

    public ProbeResult {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** 全部通过。 */
    public static ProbeResult ok(List<ProbeItem> items) {
        return new ProbeResult(true, items);
    }

    /** 不可达（附原因项）。 */
    public static ProbeResult failed(List<ProbeItem> items) {
        return new ProbeResult(false, items);
    }

    /** 全部探测项是否通过。 */
    public boolean allOk() {
        return reachable && items.stream().allMatch(ProbeItem::ok);
    }

    /** 未通过项。 */
    public List<ProbeItem> failures() {
        return items.stream().filter(item -> !item.ok()).toList();
    }

    /**
     * 单项探测结果。
     *
     * @param name    探测项名称（如 {@code auth} / {@code fetch-org-unit}）
     * @param ok      是否通过
     * @param message 说明或失败原因
     */
    public record ProbeItem(String name, boolean ok, String message) {

        public static ProbeItem pass(String name) {
            return new ProbeItem(name, true, null);
        }

        public static ProbeItem fail(String name, String message) {
            return new ProbeItem(name, false, message);
        }
    }
}
