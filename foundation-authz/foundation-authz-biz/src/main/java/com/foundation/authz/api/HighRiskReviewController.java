package com.foundation.authz.api;

import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 高风险授权复核 REST（F-09 BAS-19 §接口 {@code /api/fdn/high-risk-reviews}）。
 *
 * <p>权限码 {@code fdn:hrgrant:review}（安全管理员）。本类不标 {@code @RestController}：由
 * {@code AuthzAutoConfiguration} 以 {@code @Bean} 装配，保证与 {@link HighRiskReviewApi} 同生命周期。
 */
@ResponseBody
@RequestMapping("/api/fdn/high-risk-reviews")
public class HighRiskReviewController {

    private final HighRiskReviewApi reviews;

    public HighRiskReviewController(HighRiskReviewApi reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    @RequirePermission("fdn:hrgrant:review")
    public HighRiskReviewPageView page(@RequestParam(required = false) String status,
                                       @RequestParam(required = false) String cursor,
                                       @RequestParam(defaultValue = "20") int size) {
        return reviews.page(status, cursor, size);
    }

    @PostMapping("/{id}/approve")
    @RequirePermission("fdn:hrgrant:review")
    public HighRiskReviewView approve(@PathVariable String id,
                                      @RequestBody(required = false) HighRiskReviewDecisionCmd cmd) {
        return reviews.approve(id, operator(), cmd == null ? null : cmd.reason());
    }

    @PostMapping("/{id}/reject")
    @RequirePermission("fdn:hrgrant:review")
    public HighRiskReviewView reject(@PathVariable String id,
                                     @RequestBody(required = false) HighRiskReviewDecisionCmd cmd) {
        return reviews.reject(id, operator(), cmd == null ? null : cmd.reason());
    }

    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }
}
