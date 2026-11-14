package com.foundation.sequence.application;

import com.foundation.common.api.dict.DictApi;
import com.foundation.common.api.dict.DictItemView;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import com.foundation.sequence.api.SequenceRuleApi;
import com.foundation.sequence.api.SequenceRuleCmd;
import com.foundation.sequence.api.SequenceRuleView;
import com.foundation.sequence.domain.CheckDigit;
import com.foundation.sequence.domain.ResetCycle;
import com.foundation.sequence.domain.SequenceGenerator;
import com.foundation.sequence.domain.SequenceIdPrefix;
import com.foundation.sequence.domain.SequenceRule;
import com.foundation.sequence.domain.SequenceRules;
import com.foundation.sequence.domain.SequenceStatus;
import com.foundation.sequence.infrastructure.SequenceRuleRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 编号规则管理服务（F-09 BAS-18 §18.2）：规则 CRUD 与预览样例。
 *
 * <p>需权限码 {@code fdn:sequence:admin}（控制器拦截）。业务码值与字典 {@code sequence_biz_code} 对应，
 * 新建时校验其已在字典登记（字典装配缺失或类型未登记时跳过，避免强耦合）。
 */
public class SequenceRuleService implements SequenceRuleApi {

    private static final String BIZ_CODE_TYPE = "sequence_biz_code";

    private final SequenceRuleRepository rules;
    private final ObjectProvider<DictApi> dict;
    private final Clock clock;

    public SequenceRuleService(SequenceRuleRepository rules, ObjectProvider<DictApi> dict, Clock clock) {
        this.rules = rules;
        this.dict = dict;
        this.clock = clock;
    }

    @Override
    public List<SequenceRuleView> rules() {
        return rules.list().stream().map(SequenceRuleService::toView).toList();
    }

    @Override
    @Transactional
    public SequenceRuleView create(SequenceRuleCmd cmd, String operator) {
        String code = SequenceRules.requireCode(cmd.code());
        if (rules.findByCode(code).isPresent()) {
            throw new BizException(ErrorCode.CONFLICT, "编号规则已存在：" + code);
        }
        requireRegisteredCode(code);
        SequenceRule rule = build(Ids.newId(SequenceIdPrefix.SEQUENCE_RULE), cmd, code);
        rules.insert(rule);
        return toView(rule);
    }

    @Override
    @Transactional
    public SequenceRuleView update(String code, SequenceRuleCmd cmd, String operator) {
        String target = SequenceRules.requireCode(code);
        SequenceRule existing = rules.findByCode(target)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "编号规则不存在：" + target));
        SequenceRule rule = build(existing.id(), cmd, target);
        rules.update(rule);
        return toView(rule);
    }

    private SequenceRule build(String id, SequenceRuleCmd cmd, String code) {
        String name = SequenceRules.requireName(cmd.name());
        String pattern = SequenceRules.requirePattern(cmd.pattern());
        String prefix = SequenceRules.normalizePrefix(cmd.prefix());
        int seqLength = SequenceRules.seqLength(cmd.seqLength());
        int step = SequenceRules.step(cmd.step());
        ResetCycle resetCycle = cmd.resetCycle() == null || cmd.resetCycle().isBlank()
                ? ResetCycle.NONE : ResetCycle.of(cmd.resetCycle());
        CheckDigit checkDigit = cmd.checkDigit() == null || cmd.checkDigit().isBlank()
                ? CheckDigit.NONE : CheckDigit.of(cmd.checkDigit());
        SequenceStatus status = cmd.status() == null || cmd.status().isBlank()
                ? SequenceStatus.ACTIVE : SequenceStatus.require(cmd.status());

        SequenceRule draft = new SequenceRule(id, code, name, pattern, prefix, seqLength, step,
                resetCycle, checkDigit, status, null, null, null);
        String sample = SequenceRules.normalizeSample(SequenceGenerator.sample(draft, LocalDate.now(clock)));
        return draft.withSample(sample);
    }

    private void requireRegisteredCode(String code) {
        DictApi api = dict.getIfAvailable();
        if (api == null) {
            return;
        }
        List<DictItemView> items;
        try {
            items = api.items(null, BIZ_CODE_TYPE);
        } catch (BizException ex) {
            // 字典类型未登记时跳过校验（运行时不依赖字典）
            return;
        }
        if (items.isEmpty()) {
            return;
        }
        boolean registered = items.stream().anyMatch(item -> code.equals(item.code()));
        if (!registered) {
            throw new BizException(ErrorCode.BAD_REQUEST, "业务码值未在字典中登记：" + code);
        }
    }

    private static SequenceRuleView toView(SequenceRule rule) {
        return new SequenceRuleView(rule.id(), rule.code(), rule.name(), rule.pattern(), rule.prefix(),
                rule.seqLength(), rule.step(), rule.resetCycle().name(), rule.checkDigit().name(),
                rule.status().name(), rule.sample());
    }
}
