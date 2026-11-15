package com.foundation.account.api;

import com.foundation.account.api.dto.OffboardingResult;
import com.foundation.account.application.OffboardingApplicationService;
import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 离职联动 REST（F-04 BAS-14 §4）：只做参数绑定与权限注解，动作链在
 * {@link OffboardingApplicationService}。
 *
 * <p>本类不标 {@code @RestController}：由 {@code AccountAutoConfiguration} 以 {@code @Bean} 装配。
 */
@ResponseBody
@RequestMapping("/api/fdn")
public class OffboardingController {

    private final OffboardingApplicationService offboarding;

    public OffboardingController(OffboardingApplicationService offboarding) {
        this.offboarding = offboarding;
    }

    /** 离职停用：人员状态置离职 + 账号停用 + 动作链（F-04 §4）。 */
    @PostMapping("/persons/{id}/offboard")
    @RequirePermission("fdn:account:admin")
    public OffboardingResult offboard(@PathVariable String id) {
        return offboarding.offboard(id, operator());
    }

    /** 当前操作人（审计用）；无登录身份时（内部任务）回退为 {@code system}。 */
    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }
}
