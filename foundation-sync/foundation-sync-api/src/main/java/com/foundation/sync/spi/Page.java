package com.foundation.sync.spi;

import java.util.List;

/**
 * 一页拉取结果（F-08 BAS-11 §2）。
 *
 * @param items   本页记录
 * @param next    下一页游标；{@code hasMore=false} 时无意义
 * @param hasMore 是否还有下一页
 * @param <T>     记录类型（通常为 {@link ExternalRecord}）
 */
public record Page<T>(List<T> items, PageCursor next, boolean hasMore) {

    public Page {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** 单页即全部（无更多）。 */
    public static <T> Page<T> last(List<T> items) {
        return new Page<>(items, PageCursor.first(), false);
    }

    /** 空页。 */
    public static <T> Page<T> empty() {
        return last(List.of());
    }

    /** 是否有数据。 */
    public boolean isEmpty() {
        return items.isEmpty();
    }
}
