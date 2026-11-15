package com.foundation.starter.web.api;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 分页/排序/过滤查询参数（F-02 BAS-24 §foundation-starter-web）。
 *
 * <p>由查询参数绑定；{@link #getPage()} 与 {@link #getSize()} 在读取时归一化，越界值不抛异常，
 * 而是夹取到合法区间。排序字段名做白名单正则过滤，非法项静默丢弃。
 */
public class PageQuery {

    /** 缺省页大小。 */
    public static final int DEFAULT_SIZE = 20;

    /** 页大小上限。 */
    public static final int MAX_SIZE = 200;

    /** 合法排序字段：可选前导 {@code -}（降序）+ 字母开头的标识符。 */
    private static final Pattern SORT_FIELD = Pattern.compile("^-?[A-Za-z][A-Za-z0-9_]*$");

    private int page = 1;
    private int size = DEFAULT_SIZE;
    private String sort;
    private Map<String, String> filter = new LinkedHashMap<>();

    /** 页码（最小 1）。 */
    public int getPage() {
        return Math.max(page, 1);
    }

    public void setPage(int page) {
        this.page = page;
    }

    /** 页大小（夹取到 1..{@link #MAX_SIZE}）。 */
    public int getSize() {
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public Map<String, String> getFilter() {
        return filter;
    }

    public void setFilter(Map<String, String> filter) {
        this.filter = filter == null ? new LinkedHashMap<>() : filter;
    }

    /** 偏移量（配合 SQL {@code LIMIT/OFFSET}）。 */
    public long getOffset() {
        return (long) (getPage() - 1) * getSize();
    }

    /** 解析排序字段：按 {@code ,} 切分并只保留合法标识符，非法项丢弃。 */
    public List<String> sortFields() {
        if (sort == null || sort.isBlank()) {
            return List.of();
        }
        return Arrays.stream(sort.split(","))
                .map(String::trim)
                .filter(field -> !field.isEmpty())
                .filter(field -> SORT_FIELD.matcher(field).matches())
                .toList();
    }
}
