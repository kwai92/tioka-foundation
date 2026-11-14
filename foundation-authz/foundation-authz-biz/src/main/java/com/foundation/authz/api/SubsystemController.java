package com.foundation.authz.api;

import com.foundation.authz.api.SelfImportApplyResult;
import com.foundation.authz.api.SelfImportItemCmd;
import com.foundation.authz.api.SelfImportPreviewView;
import com.foundation.authz.api.SelfManagedImportApi;
import com.foundation.authz.api.SubsystemApi;
import com.foundation.authz.api.SubsystemView;
import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 子系统 REST（F-05 BAS-27 §接口）：子系统列表、授权模式切换与自管一次性导入。
 *
 * <p>只做参数绑定与权限注解，业务在 {@link com.foundation.authz.application.SubsystemDirectoryService}
 * 与 {@link com.foundation.authz.application.SelfManagedImporter}。
 *
 * <p>本类不标 {@code @RestController}：由 {@code AuthzAutoConfiguration} 以 {@code @Bean} 装配，
 * 保证与 {@link SubsystemApi} 等依赖同生命周期（无持久化上下文时不注册）。
 */
@ResponseBody
@RequestMapping("/api/subsystems")
public class SubsystemController {

    private final SubsystemApi subsystems;
    private final SelfManagedImportApi selfImports;

    public SubsystemController(SubsystemApi subsystems, SelfManagedImportApi selfImports) {
        this.subsystems = subsystems;
        this.selfImports = selfImports;
    }

    @GetMapping
    @RequirePermission("fdn:subsystem:read")
    public List<SubsystemView> list() {
        return subsystems.list();
    }

    @PutMapping("/{code}/auth-mode")
    @RequirePermission("fdn:subsystem:admin")
    public SubsystemView changeAuthMode(@PathVariable String code, @RequestBody AuthModeRequest request) {
        return subsystems.changeAuthMode(code, request.authMode(), operator());
    }

    /** 自管一次性导入：先预览（不落角色/授权）。 */
    @PostMapping("/{code}/self-imports")
    @RequirePermission("fdn:subsystem:admin")
    public SelfImportPreviewView preview(@PathVariable String code, @RequestBody SelfImportRequest request) {
        return selfImports.preview(code, request.items(), operator());
    }

    /** 自管一次性导入：生效（幂等），生效后子系统改为集中授权。 */
    @PostMapping("/{code}/self-imports/{batchId}/apply")
    @RequirePermission("fdn:subsystem:admin")
    public SelfImportApplyResult apply(@PathVariable String code, @PathVariable String batchId) {
        return selfImports.apply(batchId, operator());
    }

    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }

    /** 授权模式切换请求体。 */
    public record AuthModeRequest(String authMode) {
    }

    /** 自管一次性导入预览请求体。 */
    public record SelfImportRequest(List<SelfImportItemCmd> items) {
    }
}
