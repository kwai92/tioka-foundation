package com.foundation.sync.adapter.ldap;

import java.util.Locale;

/**
 * objectGUID / entryUUID 归一工具（F-08 BAS-12）。
 *
 * <p>纯静态工具：AD 的 {@code objectGUID} 为 16 字节二进制，按 {@link #normalize(byte[])} 归一为
 * 小写十六进制作为外部 ID；{@link #dashedUuid(byte[])} 仅用于展示（AD 惯例的虚线 UUID）。
 */
public final class ObjectGuidCodec {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private ObjectGuidCodec() {
    }

    /** 逐字节转小写十六进制拼接；null → null；空数组 → {@code ""}。 */
    public static String hex(byte[] guid) {
        if (guid == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(guid.length * 2);
        for (byte b : guid) {
            sb.append(HEX[(b >> 4) & 0x0F]).append(HEX[b & 0x0F]);
        }
        return sb.toString();
    }

    /**
     * AD 惯例的虚线 UUID 形式：前 3 段按 little-endian 反转，全大写。
     *
     * <p>null 或长度非 16 → null。
     */
    public static String dashedUuid(byte[] guid) {
        if (guid == null || guid.length != 16) {
            return null;
        }
        String h = hex(guid).toUpperCase(Locale.ROOT);
        return h.substring(6, 8) + h.substring(4, 6) + h.substring(2, 4) + h.substring(0, 2)
                + "-" + h.substring(10, 12) + h.substring(8, 10)
                + "-" + h.substring(14, 16) + h.substring(12, 14)
                + "-" + h.substring(16, 20)
                + "-" + h.substring(20, 32);
    }

    /** OpenLDAP {@code entryUUID} 归一：trim + 转小写；null/空白 → null。 */
    public static String entryUuid(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    /** 外部 ID 的规范形式（{@link #hex(byte[])}）。 */
    public static String normalize(byte[] guid) {
        return hex(guid);
    }
}
