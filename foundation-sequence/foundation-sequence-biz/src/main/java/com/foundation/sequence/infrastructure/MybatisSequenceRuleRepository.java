package com.foundation.sequence.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.sequence.domain.CheckDigit;
import com.foundation.sequence.domain.ResetCycle;
import com.foundation.sequence.domain.SequenceRule;
import com.foundation.sequence.domain.SequenceStatus;
import com.foundation.sequence.infrastructure.mapper.SequenceRuleMapper;
import com.foundation.sequence.infrastructure.persistence.SequenceRuleDO;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 编号规则仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisSequenceRuleRepository implements SequenceRuleRepository {

    private final SequenceRuleMapper mapper;

    public MybatisSequenceRuleRepository(SequenceRuleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<SequenceRule> list() {
        return mapper.selectList(Wrappers.<SequenceRuleDO>lambdaQuery()
                        .orderByAsc(SequenceRuleDO::getCode))
                .stream().map(MybatisSequenceRuleRepository::toDomain).toList();
    }

    @Override
    public Optional<SequenceRule> findByCode(String code) {
        SequenceRuleDO row = mapper.selectOne(Wrappers.<SequenceRuleDO>lambdaQuery()
                .eq(SequenceRuleDO::getCode, code)
                .last("limit 1"));
        return Optional.ofNullable(row).map(MybatisSequenceRuleRepository::toDomain);
    }

    @Override
    public void insert(SequenceRule rule) {
        SequenceRuleDO row = new SequenceRuleDO();
        row.setId(rule.id());
        row.setCode(rule.code());
        applyMutable(row, rule);
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        row.setCreateAt(rule.createAt() == null ? now : rule.createAt());
        row.setUpdateAt(rule.updateAt() == null ? row.getCreateAt() : rule.updateAt());
        mapper.insert(row);
    }

    @Override
    public void update(SequenceRule rule) {
        SequenceRuleDO row = new SequenceRuleDO();
        row.setId(rule.id());
        applyMutable(row, rule);
        row.setUpdateAt(LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
        mapper.updateById(row);
    }

    private static void applyMutable(SequenceRuleDO row, SequenceRule rule) {
        row.setCode(rule.code());
        row.setName(rule.name());
        row.setPattern(rule.pattern());
        row.setPrefix(rule.prefix());
        row.setSeqLength(rule.seqLength());
        row.setStep(rule.step());
        row.setResetCycle(rule.resetCycle().name());
        row.setCheckDigit(rule.checkDigit().name());
        row.setStatus(rule.status().name());
        row.setSample(rule.sample());
    }

    private static SequenceRule toDomain(SequenceRuleDO row) {
        return new SequenceRule(row.getId(), row.getCode(), row.getName(), row.getPattern(), row.getPrefix(),
                row.getSeqLength() == null ? 4 : row.getSeqLength(),
                row.getStep() == null ? 1 : row.getStep(),
                ResetCycle.of(row.getResetCycle()), CheckDigit.of(row.getCheckDigit()),
                SequenceStatus.of(row.getStatus()), row.getSample(),
                row.getCreateAt(), row.getUpdateAt());
    }
}
