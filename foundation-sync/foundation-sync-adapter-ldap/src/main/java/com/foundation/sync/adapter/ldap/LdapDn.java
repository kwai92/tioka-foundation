package com.foundation.sync.adapter.ldap;

import java.util.ArrayList;
import java.util.List;

/**
 * LDAP DN 解析工具（F-08 BAS-12）。
 *
 * <p>纯静态工具：DN 的书写顺序为「叶子在左、根在右」，本类提供叶子在左的有序解析，
 * 以及按根到叶顺序的组织路径推导（{@link #orgPath(String)}）。同名 OU 依赖该全路径区分。
 */
public final class LdapDn {

    private LdapDn() {
    }

    /**
     * 解析 DN 为有序（叶子在左）的 {@code {key, value}} 列表。
     *
     * <p>对 {@code \,} 转义逗号与引号做基本处理；null/空白 → 空列表；无法解析的片段被忽略（不抛异常）。
     */
    public static List<String[]> parse(String dn) {
        List<String[]> result = new ArrayList<>();
        for (String rdn : splitRdnStrings(dn)) {
            String[] kv = splitRdn(rdn);
            if (kv != null) {
                result.add(kv);
            }
        }
        return result;
    }

    /**
     * 只取 key 为 {@code ou} 或 {@code dc}（大小写不敏感）的分量值，按 DN 从根到叶顺序返回。
     *
     * <p>例：{@code OU=研发部,OU=技术中心,DC=example,DC=com} → {@code [com, example, 技术中心, 研发部]}。
     */
    public static List<String> components(String dn) {
        List<String[]> parsed = parse(dn);
        List<String> values = new ArrayList<>();
        // parse 为叶子在左的顺序；先收集再反转得到根到叶。
        for (int i = parsed.size() - 1; i >= 0; i--) {
            String[] kv = parsed.get(i);
            if (isOrgComponent(kv[0])) {
                values.add(kv[1]);
            }
        }
        return values;
    }

    /** {@code String.join("/", components(dn))}；无分量 → {@code ""}。 */
    public static String orgPath(String dn) {
        return String.join("/", components(dn));
    }

    /** 组织深度 = ou/dc 分量个数。 */
    public static int depth(String dn) {
        return components(dn).size();
    }

    /** 去掉最左（叶子）一个 RDN 后的剩余 DN；无剩余 → {@code ""}。 */
    public static String parentDn(String dn) {
        List<String> rdns = splitRdnStrings(dn);
        if (rdns.size() <= 1) {
            return "";
        }
        return String.join(",", rdns.subList(1, rdns.size()));
    }

    /** DN 的组织深度是否不超过 {@code maxDepth}。 */
    public static boolean withinDepth(String dn, int maxDepth) {
        return depth(dn) <= maxDepth;
    }

    private static boolean isOrgComponent(String key) {
        return "ou".equalsIgnoreCase(key) || "dc".equalsIgnoreCase(key);
    }

    /** 按未转义的逗号（引号外）切分 DN，保留各 RDN 原文（已 trim）。 */
    private static List<String> splitRdnStrings(String dn) {
        List<String> rdns = new ArrayList<>();
        if (dn == null || dn.isBlank()) {
            return rdns;
        }
        String s = dn.trim();
        boolean escaped = false;
        boolean inQuotes = false;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                String part = s.substring(start, i).trim();
                if (!part.isEmpty()) {
                    rdns.add(part);
                }
                start = i + 1;
            }
        }
        String tail = s.substring(start).trim();
        if (!tail.isEmpty()) {
            rdns.add(tail);
        }
        return rdns;
    }

    /** 拆分单个 RDN 为 {@code {key, value}}；无法解析时返回 null。 */
    private static String[] splitRdn(String rdn) {
        boolean escaped = false;
        boolean inQuotes = false;
        int eq = -1;
        for (int i = 0; i < rdn.length(); i++) {
            char c = rdn.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == '=' && !inQuotes) {
                eq = i;
                break;
            }
        }
        if (eq <= 0) {
            return null;
        }
        String key = unescape(rdn.substring(0, eq).trim());
        String value = unescape(rdn.substring(eq + 1).trim());
        if (key.isEmpty() || value.isEmpty()) {
            return null;
        }
        return new String[]{key, value};
    }

    private static String unescape(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            s = s.substring(1, s.length() - 1);
        }
        StringBuilder sb = new StringBuilder(s.length());
        boolean escaped = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}
