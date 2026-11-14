package com.foundation.sync.spi;

/**
 * 全量分页游标（F-08 BAS-11 §2）：由引擎透传，适配器内部实现分页、限流与退避重试。
 *
 * @param token 来源侧分页令牌（如飞书 {@code page_token}），首页为空
 * @param index 已拉取页序号（从 0 起）
 */
public record PageCursor(String token, int index) {

    /** 首页（无令牌）。 */
    public static PageCursor first() {
        return new PageCursor(null, 0);
    }

    /** 下一页。 */
    public PageCursor next(String nextToken) {
        return new PageCursor(nextToken, index + 1);
    }
}
