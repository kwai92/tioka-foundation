package com.foundation.app.infrastructure;

import java.util.Arrays;
import java.util.List;

/** 逗号分隔文本列与列表的互转（客户端回调地址、授权类型）。 */
final class CsvLists {

    private CsvLists() {
    }

    static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    static String join(List<String> values) {
        return values == null ? "" : String.join(",", values);
    }
}
