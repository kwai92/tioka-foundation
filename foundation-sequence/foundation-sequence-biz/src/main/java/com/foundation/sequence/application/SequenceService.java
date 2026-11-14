package com.foundation.sequence.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.sequence.api.SequenceApi;
import com.foundation.sequence.api.SequenceBatch;
import com.foundation.sequence.api.SequenceCurrentView;
import com.foundation.sequence.config.SequenceProperties;
import com.foundation.sequence.domain.SequenceGenerator;
import com.foundation.sequence.domain.SequenceRule;
import com.foundation.sequence.domain.SequenceRules;
import com.foundation.sequence.domain.SequenceStatus;
import com.foundation.sequence.infrastructure.SequenceCounterRepository;
import com.foundation.sequence.infrastructure.SequenceRuleRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 取号服务（F-09 BAS-18 §18.2）：按规则渲染业务编号，保证并发唯一。
 *
 * <p>取号以数据库为单一权威（一条原子语句完成行锁与自增），允许跳号；
 * 重置周期只决定 {@code period_key}（{@code -} / {@code yyyyMMdd} / {@code yyyyMM} / {@code yyyy}），
 * 周期切换自然落到新行、从 1 起算，无需定时任务。
 * 传 {@code Idempotency-Key} 时同一键在 TTL 内返回同一批值。
 */
public class SequenceService implements SequenceApi {

    private final SequenceRuleRepository rules;
    private final SequenceCounterRepository counters;
    private final SequenceIdempotencyStore idempotency;
    private final SequenceProperties properties;
    private final Clock clock;

    public SequenceService(SequenceRuleRepository rules, SequenceCounterRepository counters,
                           SequenceIdempotencyStore idempotency, SequenceProperties properties, Clock clock) {
        this.rules = rules;
        this.counters = counters;
        this.idempotency = idempotency;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SequenceBatch next(String ruleCode, int count, String idempotencyKey) {
        String code = SequenceRules.requireCode(ruleCode);
        int batchSize = requireCount(count);
        Optional<Object> cached = idempotency.get(idempotencyKey);
        if (cached.isPresent()) {
            return (SequenceBatch) cached.get();
        }

        SequenceRule rule = rules.findByCode(code)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "编号规则不存在：" + code));
        if (rule.status() != SequenceStatus.ACTIVE) {
            throw new BizException(ErrorCode.CONFLICT, "编号规则已停用：" + code);
        }

        LocalDate today = LocalDate.now(clock);
        String periodKey = rule.resetCycle().periodKey(today);
        long step = rule.step();
        long end = counters.next(rule.code(), periodKey, (long) batchSize * step);

        List<String> values = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            long sequence = end - (long) (batchSize - 1 - i) * step;
            values.add(SequenceGenerator.render(rule, sequence, today));
        }
        SequenceBatch batch = new SequenceBatch(List.copyOf(values));
        idempotency.put(idempotencyKey, batch);
        return batch;
    }

    @Override
    public SequenceCurrentView current(String ruleCode) {
        String code = SequenceRules.requireCode(ruleCode);
        SequenceRule rule = rules.findByCode(code)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "编号规则不存在：" + code));
        String periodKey = rule.resetCycle().periodKey(LocalDate.now(clock));
        return new SequenceCurrentView(code, periodKey, counters.current(rule.code(), periodKey));
    }

    private int requireCount(int count) {
        int value = count <= 0 ? 1 : count;
        if (value > properties.getMaxCount()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "单次取号个数超出上限：" + properties.getMaxCount());
        }
        return value;
    }
}
