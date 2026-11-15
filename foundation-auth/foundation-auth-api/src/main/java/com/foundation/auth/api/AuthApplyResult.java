package com.foundation.auth.api;

import java.util.List;

/**
 * 一次下发的整体结果（F-04 BAS-15 §4）。
 *
 * @param success 是否全部成功
 * @param items   逐项结果
 */
public record AuthApplyResult(boolean success, List<AuthApplyItem> items) {

    /** 由逐项结果汇总：全部成功才为成功。 */
    public static AuthApplyResult of(List<AuthApplyItem> items) {
        boolean ok = items != null && !items.isEmpty() && items.stream().allMatch(AuthApplyItem::success);
        return new AuthApplyResult(ok, items == null ? List.of() : List.copyOf(items));
    }
}
