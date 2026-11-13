package com.foundation.common.id;

import com.github.f4b6a3.ulid.UlidCreator;

/** ULID 主键生成（00-施工总纲 §2.2：{@code {前缀}_{ulid}}）。 */
public final class Ids {

    private Ids() {
    }

    public static String newId(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix required");
        }
        return prefix + "_" + UlidCreator.getMonotonicUlid();
    }
}
