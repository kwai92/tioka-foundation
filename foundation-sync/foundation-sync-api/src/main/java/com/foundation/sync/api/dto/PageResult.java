package com.foundation.sync.api.dto;

import java.util.List;

/**
 * 分页结果（F-08 BAS-11 §10 列表接口）。
 *
 * @param items 当前页数据
 * @param total 总行数
 * @param page  页码（从 1 起）
 * @param size  每页行数
 */
public record PageResult<T>(List<T> items, long total, int page, int size) {

    public static <T> PageResult<T> of(List<T> items, long total, int page, int size) {
        return new PageResult<>(items, total, page, size);
    }
}
