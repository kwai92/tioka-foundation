package com.foundation.sequence.api;

import com.foundation.starter.security.permission.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 序列取号 REST（F-09 BAS-18 §18.2 {@code /api/fdn/sequences:next}）。
 *
 * <p>取号供应用服务账号调用（需登录身份），按 {@code Idempotency-Key} 幂等；
 * 查询当前周期值需权限码 {@code fdn:sequence:admin}（排障用）。
 * 本类不标 {@code @RestController}：由 {@code SequenceAutoConfiguration} 以 {@code @Bean} 装配。
 */
@ResponseBody
@RequestMapping("/api/fdn")
public class SequenceController {

    private static final String ADMIN = "fdn:sequence:admin";

    private final SequenceApi sequences;

    public SequenceController(SequenceApi sequences) {
        this.sequences = sequences;
    }

    /** 取号：{@code {ruleCode, count?}} → {@code {values[]}}。 */
    @PostMapping("/sequences:next")
    public SequenceBatch next(@RequestBody NextSequenceCmd cmd,
                             @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        int count = cmd.count() == null ? 1 : cmd.count();
        return sequences.next(cmd.ruleCode(), count, idempotencyKey);
    }

    /** 当前周期已用值（排障）。 */
    @GetMapping("/sequences/{ruleCode}/current")
    @RequirePermission(ADMIN)
    public SequenceCurrentView current(@PathVariable String ruleCode) {
        return sequences.current(ruleCode);
    }
}
