package com.foundation.app.api;

import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * 应用登记 REST（F-05 BAS-04 §接口 {@code /api/apps}）：列表、登记与修改。
 *
 * <p>读 {@code fdn:app:read}，写 {@code fdn:app:admin}；登记自动开通 OIDC 客户端（{@link AppApi#register}），
 * {@code code} 登记后不可改。应用下的客户端与权限清单分别见 {@link AppClientController} 与 authz 侧清单接口。
 *
 * <p>本类不标 {@code @RestController}：由 {@code FoundationAppAutoConfiguration} 以 {@code @Bean} 装配，
 * 保证与 {@link AppApi} 同生命周期（无持久化上下文时不注册）。
 */
@ResponseBody
@RequestMapping("/api/apps")
public class AppController {

    private final AppApi apps;

    public AppController(AppApi apps) {
        this.apps = apps;
    }

    /** 应用列表（按编码升序）。 */
    @GetMapping
    @RequirePermission("fdn:app:read")
    public List<AppView> list() {
        return apps.list();
    }

    /** 登记应用并自动开通客户端；返回落库后的应用视图。 */
    @PostMapping
    @RequirePermission("fdn:app:admin")
    @ResponseStatus(HttpStatus.CREATED)
    public AppView register(@RequestBody AppRequest request) {
        String id = apps.register(new AppRegistrationCmd(request.code(), request.name(), request.icon(),
                request.accessUrl(), request.ownerUserId(), request.kind(), request.redirectUris(),
                operator()));
        return apps.list().stream()
                .filter(view -> view.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    /** 修改应用（编码以路径为准，不可改）。 */
    @PutMapping("/{code}")
    @RequirePermission("fdn:app:admin")
    public AppView update(@PathVariable String code, @RequestBody AppRequest request) {
        apps.update(new AppUpdateCmd(code, request.name(), request.icon(), request.accessUrl(),
                request.ownerUserId(), request.status(), operator()));
        return apps.find(code).orElseThrow();
    }

    /** 当前操作人（审计用）；无登录身份时（内部任务）回退为 {@code system}。 */
    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }

    /** 应用登记 / 修改请求体；登记用 {@code code}/{@code kind}/{@code redirectUris}，修改用 {@code status}。 */
    public record AppRequest(String code,
                             String name,
                             String icon,
                             String accessUrl,
                             String ownerUserId,
                             String kind,
                             List<String> redirectUris,
                             String status) {
    }
}
