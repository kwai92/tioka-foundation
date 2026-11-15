package com.foundation.starter.web.api;

import java.util.List;

/**
 * 分页结果（F-02 BAS-24 §foundation-starter-web）。
 *
 * @param items 当前页数据
 * @param total 总记录数
 * @param page  当前页码（来自 {@link PageQuery} 归一化后的值）
 * @param size  当前页大小（来自 {@link PageQuery} 归一化后的值）
 * @param <T>   数据类型
 */
public record PageResult<T>(List<T> items, long total, int page, int size) {

    /** 由查询参数与数据构造分页结果。 */
    public static <T> PageResult<T> of(List<T> items, long total, PageQuery query) {
        return new PageResult<>(items, total, query.getPage(), query.getSize());
    }
}
