package com.foundation.starter.cache.key;

/**
 * 缓存键前缀（F-02 BAS-24 §foundation-starter-cache）。
 *
 * <p>统一收敛键的命名：{@code keyPrefix + part1:part2:...}，避免散落的字符串拼接。
 */
public class KeyPrefix {

    private final String keyPrefix;

    public KeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix == null ? "" : keyPrefix;
    }

    /** 拼接键：无段时只返回前缀，否则以 {@code :} 连接各段。 */
    public String of(String... parts) {
        if (parts == null || parts.length == 0) {
            return keyPrefix;
        }
        return keyPrefix + String.join(":", parts);
    }

    /** 返回裸前缀（不拼接任何段）。 */
    public String raw() {
        return keyPrefix;
    }
}
