package com.foundation.authz.api;

/**
 * 权限清单导入契约（F-05 BAS-05 §接口 {@code POST /api/apps/{code}/manifest}）。
 *
 * <p>权限码 {@code fdn:manifest:import}（MEDIUM），由应用 CI 服务账号调用。
 */
public interface ManifestApi {

    /**
     * 导入应用权限清单，规则见 F-05 BAS-05 §清单导入规则。
     *
     * <p>RFC 8785 规范化 + SHA-256 得 {@code content_hash}：命中 {@code (app_id, content_hash)} 幂等返回；
     * 版本必须大于 {@code fdn_app.manifest_version}，否则 {@code CONFLICT}。
     *
     * @param appCode       应用编码
     * @param manifestBytes 清单原文（UTF-8 JSON）
     * @param importSource  导入来源（如 {@code ci@contract}）
     * @param operator      操作人（审计列 {@code imported_by} / {@code update_by}）
     */
    ManifestImportResult importManifest(String appCode, byte[] manifestBytes, String importSource,
                                        String operator);
}
