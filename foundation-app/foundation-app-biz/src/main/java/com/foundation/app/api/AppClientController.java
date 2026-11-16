package com.foundation.app.api;

import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 应用客户端 REST（F-05 BAS-04 §接口）：客户端列表与密钥轮换。
 *
 * <p>读 {@code fdn:app:read}，轮换 {@code fdn:app:admin}；轮换由 Keycloak 生成新密钥并立即作废旧密钥，
 * 密钥明文只经本端点交付一次，Foundation 不落库。
 *
 * <p>本类不标 {@code @RestController}：由 {@code FoundationAppAutoConfiguration} 以 {@code @Bean} 装配。
 */
@ResponseBody
@RequestMapping("/api/apps/{code}/clients")
public class AppClientController {

    private final AppClientApi clients;

    public AppClientController(AppClientApi clients) {
        this.clients = clients;
    }

    /** 某应用下的客户端列表（按 clientId 升序）。 */
    @GetMapping
    @RequirePermission("fdn:app:read")
    public List<ClientView> list(@PathVariable String code) {
        return clients.listByApp(code);
    }

    /** 轮换客户端密钥，返回新密钥明文（唯一交付出口）。 */
    @PostMapping("/{clientId}/rotate-secret")
    @RequirePermission("fdn:app:admin")
    public RotatedSecret rotateSecret(@PathVariable String code, @PathVariable String clientId) {
        String secret = clients.rotateSecret(code, clientId, operator());
        return new RotatedSecret(clientId, secret);
    }

    /** 当前操作人（审计用）；无登录身份时（内部任务）回退为 {@code system}。 */
    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }

    /** 密钥轮换响应：新密钥明文仅此一次返回。 */
    public record RotatedSecret(String clientId, String clientSecret) {
    }
}
