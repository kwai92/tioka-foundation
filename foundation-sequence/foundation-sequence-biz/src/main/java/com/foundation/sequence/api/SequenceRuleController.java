package com.foundation.sequence.api;

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
 * 编号规则管理 REST（F-09 BAS-18 §18.2 {@code /api/fdn/sequence-rules}）：规则列表、新建与修改（含预览样例）。
 *
 * <p>需权限码 {@code fdn:sequence:admin}。本类不标 {@code @RestController}：
 * 由 {@code SequenceAutoConfiguration} 以 {@code @Bean} 装配。
 */
@ResponseBody
@RequestMapping("/api/fdn/sequence-rules")
public class SequenceRuleController {

    private static final String ADMIN = "fdn:sequence:admin";

    private final SequenceRuleApi rules;

    public SequenceRuleController(SequenceRuleApi rules) {
        this.rules = rules;
    }

    /** 规则列表。 */
    @GetMapping
    @RequirePermission(ADMIN)
    public List<SequenceRuleView> rules() {
        return rules.rules();
    }

    /** 新建规则。 */
    @PostMapping
    @RequirePermission(ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    public SequenceRuleView create(@RequestBody SequenceRuleCmd cmd) {
        return rules.create(cmd, operator());
    }

    /** 修改规则（按业务码值定位）。 */
    @PutMapping("/{code}")
    @RequirePermission(ADMIN)
    public SequenceRuleView update(@PathVariable String code, @RequestBody SequenceRuleCmd cmd) {
        return rules.update(code, cmd, operator());
    }

    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }
}
