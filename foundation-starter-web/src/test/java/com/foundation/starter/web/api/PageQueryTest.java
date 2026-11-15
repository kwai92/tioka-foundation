package com.foundation.starter.web.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 分页查询参数与结果（F-02 BAS-24 §foundation-starter-web）：默认值、越界归一化、排序字段过滤。 */
class PageQueryTest {

    @Test
    void defaultsAreApplied() {
        PageQuery query = new PageQuery();

        assertThat(query.getPage()).isEqualTo(1);
        assertThat(query.getSize()).isEqualTo(PageQuery.DEFAULT_SIZE);
        assertThat(query.getFilter()).isEmpty();
        assertThat(query.sortFields()).isEmpty();
    }

    @Test
    void pageAndSizeAreNormalized() {
        PageQuery query = new PageQuery();
        query.setPage(0);
        query.setSize(0);
        assertThat(query.getPage()).isEqualTo(1);
        assertThat(query.getSize()).isEqualTo(1);

        query.setPage(-5);
        query.setSize(9999);
        assertThat(query.getPage()).isEqualTo(1);
        assertThat(query.getSize()).isEqualTo(PageQuery.MAX_SIZE);
    }

    @Test
    void offsetIsComputedFromNormalizedValues() {
        PageQuery query = new PageQuery();
        query.setPage(3);
        query.setSize(20);

        assertThat(query.getOffset()).isEqualTo(40L);
    }

    @Test
    void sortFieldsFilterInvalidItems() {
        PageQuery query = new PageQuery();
        query.setSort("name,-createdAt, 2bad ,drop table,ok_1");

        assertThat(query.sortFields()).containsExactly("name", "-createdAt", "ok_1");
    }

    @Test
    void pageResultDerivesFromQuery() {
        PageQuery query = new PageQuery();
        query.setPage(2);
        query.setSize(50);

        PageResult<String> result = PageResult.of(List.of("a", "b"), 120L, query);

        assertThat(result.items()).containsExactly("a", "b");
        assertThat(result.total()).isEqualTo(120L);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(50);
    }
}
