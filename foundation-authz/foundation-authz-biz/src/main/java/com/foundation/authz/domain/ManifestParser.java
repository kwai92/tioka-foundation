package com.foundation.authz.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限清单解析（F-05 BAS-05 §清单导入规则）。
 *
 * <p>只接受固定形态：{@code {"version":N,"menus":[...],"permissions":[...]}}；
 * 未知字段被忽略（不参与内容哈希），重复编码、非法类型等一律 {@code BAD_REQUEST}。
 */
public class ManifestParser {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final JsonNode EMPTY_ARRAY = MAPPER.createArrayNode();

    /** 解析清单原文（UTF-8 JSON）为文档模型。 */
    public ManifestDocument parse(byte[] manifestBytes) {
        JsonNode root = readTree(manifestBytes);
        if (root == null || !root.isObject()) {
            throw bad("清单必须是 JSON 对象");
        }
        long version = requirePositiveLong(root, "version");

        List<ManifestMenu> menus = new ArrayList<>();
        Set<String> menuCodes = new HashSet<>();
        for (JsonNode node : arrayOf(root, "menus")) {
            ManifestMenu menu = menu(node);
            if (!menuCodes.add(menu.code())) {
                throw bad("清单内菜单编码重复：" + menu.code());
            }
            menus.add(menu);
        }

        List<ManifestPermission> permissions = new ArrayList<>();
        Set<String> permissionCodes = new HashSet<>();
        for (JsonNode node : arrayOf(root, "permissions")) {
            ManifestPermission permission = permission(node);
            if (!permissionCodes.add(permission.code())) {
                throw bad("清单内权限码重复：" + permission.code());
            }
            permissions.add(permission);
        }
        return new ManifestDocument(version, menus, permissions);
    }

    private JsonNode readTree(byte[] manifestBytes) {
        if (manifestBytes == null || manifestBytes.length == 0) {
            throw bad("清单内容为空");
        }
        try {
            return MAPPER.readTree(manifestBytes);
        } catch (IOException ex) {
            throw bad("清单不是合法 JSON");
        }
    }

    private ManifestMenu menu(JsonNode node) {
        if (!node.isObject()) {
            throw bad("menus 的每一项必须是 JSON 对象");
        }
        String code = requireText(node, "code", "菜单");
        String name = requireText(node, "name", "菜单 " + code);
        PermissionCatalog.validateMenuCode(code);
        MenuType type = MenuType.of(text(node, "type"));
        String parentCode = blankToNull(text(node, "parent"));
        String permissionCode = blankToNull(text(node, "permissionCode"));
        if (permissionCode != null) {
            PermissionCatalog.validatePermissionCode(permissionCode);
        }
        return new ManifestMenu(code, name, type, parentCode, text(node, "routePath"), text(node, "component"),
                text(node, "icon"), intValue(node, "sort"), booleanValue(node, "visible", true), permissionCode);
    }

    private ManifestPermission permission(JsonNode node) {
        if (!node.isObject()) {
            throw bad("permissions 的每一项必须是 JSON 对象");
        }
        String code = requireText(node, "code", "权限码");
        PermissionCatalog.validatePermissionCode(code);
        return new ManifestPermission(code, text(node, "name"), RiskLevel.of(text(node, "riskLevel")));
    }

    private static JsonNode arrayOf(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return EMPTY_ARRAY;
        }
        if (!node.isArray()) {
            throw bad(field + " 必须是 JSON 数组");
        }
        return node;
    }

    private static long requirePositiveLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong()) {
            throw bad("缺少整数字段 " + field);
        }
        long parsed = value.asLong();
        if (parsed <= 0) {
            throw bad(field + " 必须为正整数");
        }
        return parsed;
    }

    private static String requireText(JsonNode node, String field, String owner) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw bad(owner + " 缺少必填字段 " + field);
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static int intValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.canConvertToInt() ? 0 : value.asInt();
    }

    private static boolean booleanValue(JsonNode node, String field, boolean defaultValue) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asBoolean();
    }

    private static BizException bad(String message) {
        return new BizException(ErrorCode.BAD_REQUEST, message);
    }
}
