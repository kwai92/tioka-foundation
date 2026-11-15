package com.foundation.app.api;

import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.Instant;
import java.util.List;

/**
 * 应用服务账号 REST（F-05 BAS-04 §接口 {@code /api/apps/{code}/service-accounts}）。
 *
 * <p>权限码 {@code fdn:service:admin}（应用侧服务账号绑定管理）；账号主体（{@code fdn_account}，
 * {@code type=SERVICE}）的生命周期与启用/停用见 F-04 的 {@code /api/fdn/service-accounts}（{@code fdn:serviceaccount:admin}）。
 *
 * <p>本类不标 {@code @RestController}：由 {@code FoundationAppAutoConfiguration} 以 {@code @Bean} 装配。
 */
@ResponseBody
@RequestMapping("/api/apps/{code}/service-accounts")
public class AppServiceAccountController {

    private final ServiceAccountApi serviceAccounts;

    public AppServiceAccountController(ServiceAccountApi serviceAccounts) {
        this.serviceAccounts = serviceAccounts;
    }

    /** 某应用下的服务账号列表（按名称升序）。 */
    @GetMapping
    @RequirePermission("fdn:service:admin")
    public List<ServiceAccountView> list(@PathVariable String code) {
        return serviceAccounts.listByApp(code);
    }

    /** 登记服务账号（同时开通 {@code client_credentials} 客户端）；返回落库后的视图。 */
    @PostMapping
    @RequirePermission("fdn:service:admin")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceAccountView create(@PathVariable String code, @RequestBody ServiceAccountRequest request) {
        String id = serviceAccounts.create(new ServiceAccountCmd(code, request.accountId(), request.name(),
                request.permissionCodes(), request.ownerUserId(), request.expiresAt(), operator()));
        return serviceAccounts.listByApp(code).stream()
                .filter(view -> view.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    /** 停用服务账号（{@code status=DISABLED}），其客户端同时停用。 */
    @DeleteMapping("/{id}")
    @RequirePermission("fdn:service:admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable String code, @PathVariable String id) {
        serviceAccounts.disable(id, operator());
    }

    /** 当前操作人（审计用）；无登录身份时（内部任务）回退为 {@code system}。 */
    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }

    /** 服务账号登记请求体。 */
    public record ServiceAccountRequest(String accountId,
                                        String name,
                                        List<String> permissionCodes,
                                        String ownerUserId,
                                        Instant expiresAt) {
    }
}
