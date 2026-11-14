package com.foundation.file.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 内容哈希（F-09 BAS-17）：SHA-256 十六进制小写，用于入库校验与去重判据。
 *
 * <p>与 {@code com.foundation.sync.scim.domain.ScimHash} 同口径（UTF-8 字节的 SHA-256 十六进制小写）。
 */
public final class Sha256 {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Sha256() {
    }

    /** 计算 SHA-256 十六进制小写；{@code content} 为空按空字节序列处理。 */
    public static String hex(byte[] content) {
        try {
            return toHex(MessageDigest.getInstance("SHA-256").digest(content == null ? new byte[0] : content));
        } catch (NoSuchAlgorithmException ex) {
            throw new BizException(ErrorCode.INTERNAL, "SHA-256 不可用");
        }
    }

    /** 流式计算 SHA-256 十六进制小写；不把内容整体驻留内存（分片合并后校验用，调用方负责关闭 {@code stream}）。 */
    public static String hex(InputStream stream) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new BizException(ErrorCode.INTERNAL, "SHA-256 不可用");
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL, "读取内容失败：" + ex.getMessage());
        }
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            out[i * 2] = HEX[value >>> 4];
            out[i * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(out);
    }

    /** 校验形如 64 位十六进制的摘要；不合法抛 {@code BAD_REQUEST(40000)}。 */
    public static String requireHex(String sha256) {
        if (sha256 == null || sha256.length() != 64) {
            throw new BizException(ErrorCode.BAD_REQUEST, "sha256 必须为 64 位十六进制");
        }
        try {
            HexFormat.of().parseHex(sha256);
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "sha256 必须为 64 位十六进制");
        }
        return sha256;
    }

    /** 定长比较（不提前短路），用于校验上传内容与声明摘要是否一致。 */
    public static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(), right.getBytes());
    }
}
