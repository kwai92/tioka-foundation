package com.foundation.authz.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 清单内容哈希（F-05 BAS-05 §清单导入规则）：RFC 8785 规范化 + SHA-256。
 *
 * <p>规范化以「解析后的文档模型」为基准：固定键序、无多余空白、数组按 {@code code} 排序，
 * 因此键序或数组顺序不同的同义清单得到同一哈希，命中 {@code (app_id, content_hash)} 即幂等返回。
 */
public final class ManifestHasher {

    private ManifestHasher() {
    }

    /** 计算清单内容哈希（小写十六进制 SHA-256）。 */
    public static String contentHash(ManifestDocument document) {
        return sha256Hex(canonicalize(document));
    }

    /** 生成规范化 JSON 文本。 */
    public static String canonicalize(ManifestDocument document) {
        List<ManifestMenu> menus = new ArrayList<>(document.menus());
        menus.sort(Comparator.comparing(ManifestMenu::code));
        List<ManifestPermission> permissions = new ArrayList<>(document.permissions());
        permissions.sort(Comparator.comparing(ManifestPermission::code));

        StringBuilder json = new StringBuilder();
        json.append("{\"version\":").append(document.version());
        json.append(",\"menus\":[");
        for (int i = 0; i < menus.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            appendMenu(json, menus.get(i));
        }
        json.append("],\"permissions\":[");
        for (int i = 0; i < permissions.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            appendPermission(json, permissions.get(i));
        }
        return json.append("]}").toString();
    }

    /** SHA-256 十六进制摘要。 */
    public static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("运行环境缺少 SHA-256", ex);
        }
    }

    private static void appendMenu(StringBuilder json, ManifestMenu menu) {
        json.append("{\"code\":").append(quote(menu.code()));
        json.append(",\"name\":").append(quote(menu.name()));
        json.append(",\"type\":").append(quote(menu.type() == null ? null : menu.type().name()));
        json.append(",\"parentCode\":").append(quote(menu.parentCode()));
        json.append(",\"routePath\":").append(quote(menu.routePath()));
        json.append(",\"component\":").append(quote(menu.component()));
        json.append(",\"icon\":").append(quote(menu.icon()));
        json.append(",\"sort\":").append(menu.sort());
        json.append(",\"visible\":").append(menu.visible());
        json.append(",\"permissionCode\":").append(quote(menu.permissionCode())).append('}');
    }

    private static void appendPermission(StringBuilder json, ManifestPermission permission) {
        json.append("{\"code\":").append(quote(permission.code()));
        json.append(",\"name\":").append(quote(permission.name()));
        json.append(",\"riskLevel\":")
                .append(quote(permission.riskLevel() == null ? null : permission.riskLevel().name())).append('}');
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }
}
