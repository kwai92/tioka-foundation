package com.foundation.sync.scim.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * SCIM 令牌与人员敏感字段的 SHA-256 工具（F-08 BAS-13 §2）。
 *
 * <p>口径与 BAS-02 的 {@code PersonCipher#hash} 一致：UTF-8 明文的 SHA-256 十六进制小写，
 * 因此 SCIM 推来的邮箱可被平台按同一哈希命中（不按姓名匹配，ADR-052 第 3 条）。
 */
public final class ScimHash {

    private ScimHash() {
    }

    /** SHA-256 十六进制小写；入参为空白返回 {@code null}。 */
    public static String sha256Hex(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return HexFormat.of().formatHex(sha256(value.getBytes(StandardCharsets.UTF_8)));
    }

    /** 常量时间比较（防令牌哈希的时序侧信道）。 */
    public static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
