package com.foundation.sync.adapter.feishu;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 飞书事件回调签名校验（F-08 BAS-12）：纯逻辑、可单测。
 *
 * <p>签名 = {@code sha256Hex(timestamp + nonce + encryptKey + body)}（UTF-8，小写 hex）；
 * 校验使用常量时间比较，忽略大小写。
 */
public class FeishuEventVerifier {

    private final String encryptKey;

    public FeishuEventVerifier(String encryptKey) {
        this.encryptKey = encryptKey;
    }

    /** 计算签名：{@code sha256Hex(timestamp + nonce + encryptKey + body)}。 */
    public static String sign(String timestamp, String nonce, String encryptKey, String body) {
        String raw = nullSafe(timestamp) + nullSafe(nonce) + nullSafe(encryptKey) + nullSafe(body);
        return sha256Hex(raw);
    }

    /** 校验签名；任何入参为 null/空白返回 false，比较忽略大小写。 */
    public boolean verify(String timestamp, String nonce, String body, String signature) {
        if (isBlank(timestamp) || isBlank(nonce) || isBlank(body) || isBlank(signature)) {
            return false;
        }
        String expected = sign(timestamp, nonce, encryptKey, body);
        return constantTimeEquals(expected, signature.trim().toLowerCase());
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
