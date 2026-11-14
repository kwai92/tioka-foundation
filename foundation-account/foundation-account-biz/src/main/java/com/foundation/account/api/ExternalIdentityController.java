package com.foundation.account.api;

import com.foundation.account.api.dto.ExternalIdentityView;
import com.foundation.account.application.ExternalIdentityApplicationService;
import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 外部身份 REST（F-04 §2、§7）：待处理关联列表与管理员确认 / 解除。
 *
 * <p>自动关联只按工号或已验证邮箱且唯一时建立；**无姓名匹配、无自动建人**。
 */
@RestController
@RequestMapping("/api/fdn/external-identities")
public class ExternalIdentityController {

    private final ExternalIdentityApplicationService identities;

    public ExternalIdentityController(ExternalIdentityApplicationService identities) {
        this.identities = identities;
    }

    /** 待处理关联列表；给 {@code personId} 时返回该人员的外部身份。 */
    @GetMapping
    @RequirePermission("fdn:identity:admin")
    public List<ExternalIdentityView> list(@RequestParam(required = false) String personId,
                                           @RequestParam(defaultValue = "true") boolean pending) {
        if (personId != null && !personId.isBlank()) {
            return identities.listByPerson(personId);
        }
        return pending ? identities.listPending() : List.of();
    }

    /** 管理员确认关联（{@code link_mode=ADMIN}）。 */
    @PostMapping("/{id}/confirm")
    @RequirePermission("fdn:identity:admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@PathVariable String id, @RequestParam String personId) {
        identities.confirm(id, personId, operator());
    }

    /** 解除关联，外部身份回到待处理状态。 */
    @PostMapping("/{id}/unlink")
    @RequirePermission("fdn:identity:admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable String id) {
        identities.unlink(id, operator());
    }

    /** 当前操作人（审计用）；无登录身份时（内部任务）回退为 {@code system}。 */
    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }
}
