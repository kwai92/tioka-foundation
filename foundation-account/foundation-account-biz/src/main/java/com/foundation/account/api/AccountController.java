package com.foundation.account.api;

import com.foundation.account.api.dto.AccountCreateCmd;
import com.foundation.account.api.dto.AccountReconcileView;
import com.foundation.account.api.dto.AccountResetResult;
import com.foundation.account.api.dto.AccountSessionView;
import com.foundation.account.api.dto.AccountStatus;
import com.foundation.account.api.dto.AccountType;
import com.foundation.account.api.dto.AccountView;
import com.foundation.account.api.dto.EmergencyEnableTicket;
import com.foundation.account.api.dto.PageResult;
import com.foundation.account.application.AccountApplicationService;
import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 账号 REST（F-04 §7）：只做参数绑定与权限注解，业务与事务边界在 {@link AccountApplicationService}。
 *
 * <p>写接口的 {@code Idempotency-Key} 由 {@code foundation-starter-web}（BAS-24）统一处理；
 * Foundation 不保存口令（重置只经 Keycloak 发邮件或返回一次性临时口令）。
 */
@RestController
@RequestMapping("/api/fdn/accounts")
public class AccountController {

    private final AccountApplicationService accounts;

    public AccountController(AccountApplicationService accounts) {
        this.accounts = accounts;
    }

    /** 账号列表（个人与应急管理员）。 */
    @GetMapping
    @RequirePermission("fdn:account:read")
    public PageResult<AccountView> list(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) AccountStatus status,
                                        @RequestParam(required = false) AccountType type,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return accounts.search(q, status, type, page, size);
    }

    /** 最近的对账记录（F-04 §6「可在管理台查询」）。 */
    @GetMapping("/reconciles")
    @RequirePermission("fdn:account:read")
    public List<AccountReconcileView> reconciles(@RequestParam(defaultValue = "20") int limit) {
        return accounts.recentReconciles(limit);
    }

    /** 开通个人账号并推送 Keycloak（先落库再推送）。 */
    @PostMapping
    @RequirePermission("fdn:account:admin")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView create(@RequestBody AccountCreateCmd cmd) {
        String id = accounts.createPersonal(new AccountCreateCmd(cmd.personId(), cmd.loginName(), operator()));
        return accounts.findById(id).orElseThrow();
    }

    /** 停用（连带吊销会话，F-04 §5）。 */
    @PutMapping("/{id}/disable")
    @RequirePermission("fdn:account:admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable String id) {
        accounts.disable(id, operator());
    }

    /** 重新开通。 */
    @PutMapping("/{id}/enable")
    @RequirePermission("fdn:account:admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enable(@PathVariable String id) {
        accounts.enable(id, operator());
    }

    /** 锁定（可解锁，与停用分开）。 */
    @PutMapping("/{id}/lock")
    @RequirePermission("fdn:account:admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void lock(@PathVariable String id) {
        accounts.lock(id, operator());
    }

    /** 解锁。 */
    @PutMapping("/{id}/unlock")
    @RequirePermission("fdn:account:admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlock(@PathVariable String id) {
        accounts.unlock(id, operator());
    }

    /** 重置口令：发重置邮件或返回一次性临时口令（不落库、不进日志）。 */
    @PutMapping("/{id}/reset-password")
    @RequirePermission("fdn:account:admin")
    public AccountResetResult resetPassword(@PathVariable String id) {
        return accounts.resetPassword(id, operator());
    }

    /** 在线会话（来自 Keycloak）。 */
    @GetMapping("/{id}/sessions")
    @RequirePermission("fdn:account:read")
    public List<AccountSessionView> sessions(@PathVariable String id) {
        return accounts.sessions(id);
    }

    /** 强制下线，发 {@code fdn.account.sessions_revoked}。 */
    @DeleteMapping("/{id}/sessions")
    @RequirePermission("fdn:account:admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSessions(@PathVariable String id) {
        accounts.revokeSessions(id, operator());
    }

    /** 应急管理员启用第一步（发起）：返回一次性凭据。 */
    @PostMapping("/emergency/{id}/enable-request")
    @RequirePermission("fdn:account:admin")
    public EmergencyEnableTicket requestEmergencyEnable(@PathVariable String id) {
        return accounts.requestEmergencyEnable(id, operator());
    }

    /** 应急管理员启用第二步（另一名系统管理员确认，四眼）。 */
    @PostMapping("/emergency/{id}/enable-confirm")
    @RequirePermission("fdn:account:admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmEmergencyEnable(@PathVariable String id, @RequestParam String requestId) {
        accounts.confirmEmergencyEnable(id, requestId, operator());
    }

    /** 当前操作人（审计用）；无登录身份时（内部任务）回退为 {@code system}。 */
    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }
}
