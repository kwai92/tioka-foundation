package com.foundation.authz.api;

import com.foundation.authz.api.ManifestApi;
import com.foundation.authz.api.ManifestImportResult;
import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 权限清单导入 REST（F-05 BAS-05 §接口 {@code POST /api/apps/{code}/manifest}）。
 *
 * <p>清单由应用 CI 服务账号提交，UTF-8 JSON 原文；权限码 {@code fdn:manifest:import}（MEDIUM）。
 *
 * <p>本类不标 {@code @RestController}：由 {@code AuthzAutoConfiguration} 以 {@code @Bean} 装配，
 * 保证与 {@link ManifestApi} 同生命周期（无持久化上下文时不注册）。
 */
@ResponseBody
@RequestMapping("/api/apps")
public class ManifestController {

    private final ManifestApi manifests;

    public ManifestController(ManifestApi manifests) {
        this.manifests = manifests;
    }

    /** 导入应用权限清单（UTF-8 JSON 原文）。 */
    @PostMapping("/{code}/manifest")
    @RequirePermission("fdn:manifest:import")
    public ManifestImportResult importManifest(
            @PathVariable String code,
            @RequestBody byte[] manifestBytes,
            @RequestHeader(value = "X-Import-Source", required = false, defaultValue = "manual")
            String importSource) {
        return manifests.importManifest(code, manifestBytes, importSource, operator());
    }

    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }
}
