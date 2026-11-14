package com.foundation.account.api;

import com.foundation.account.api.dto.AccountView;
import com.foundation.account.api.dto.ServiceAccountCreateCmd;
import com.foundation.account.application.ServiceAccountApplicationService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务账号 REST（F-04 §3、§7）：账号主体在 {@code fdn_account}（{@code type=SERVICE}），
 * 应用绑定、权限码白名单与 {@code client_credentials} 客户端由 BAS-04 维护。
 */
@RestController
@RequestMapping("/api/fdn/service-accounts")
public class ServiceAccountController {

    private final ServiceAccountApplicationService serviceAccounts;

    public ServiceAccountController(ServiceAccountApplicationService serviceAccounts) {
        this.serviceAccounts = serviceAccounts;
    }

    /** 服务账号列表；{@code appCode} 为空表示全部。 */
    @GetMapping
    @RequirePermission("fdn:serviceaccount:admin")
    public List<AccountView> list(@RequestParam(required = false) String appCode) {
        return serviceAccounts.list(appCode);
    }

    /** 新建服务账号（先落库并推送 Keycloak，再由 BAS-04 登记绑定与客户端）。 */
    @PostMapping
    @RequirePermission("fdn:serviceaccount:admin")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView create(@RequestBody ServiceAccountCreateCmd cmd) {
        String id = serviceAccounts.create(new ServiceAccountCreateCmd(cmd.appCode(), cmd.name(),
                cmd.permissionCodes(), cmd.ownerPersonId(), cmd.expiresAt(), operator()));
        return serviceAccounts.findById(id).orElseThrow();
    }

    /** 停用服务账号（连带停用 BAS-04 绑定）。 */
    @PutMapping("/{id}/disable")
    @RequirePermission("fdn:serviceaccount:admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable String id) {
        serviceAccounts.disable(id, operator());
    }

    /** 启用服务账号。 */
    @PutMapping("/{id}/enable")
    @RequirePermission("fdn:serviceaccount:admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enable(@PathVariable String id) {
        serviceAccounts.enable(id, operator());
    }

    /** 当前操作人（审计用）；无登录身份时（内部任务）回退为 {@code system}。 */
    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }
}
