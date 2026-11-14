package com.foundation.sequence.application;

import com.foundation.sequence.domain.SequenceRule;
import com.foundation.sequence.infrastructure.SequenceCounterRepository;
import com.foundation.sequence.infrastructure.SequenceRuleRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 编号与序列应用层单测的内存替身。 */
final class SequenceTestDoubles {

    private SequenceTestDoubles() {
    }

    /** 内存编号规则仓储。 */
    static final class InMemorySequenceRuleRepository implements SequenceRuleRepository {

        final Map<String, SequenceRule> byId = new LinkedHashMap<>();

        @Override
        public List<SequenceRule> list() {
            return byId.values().stream().sorted((a, b) -> a.code().compareTo(b.code())).toList();
        }

        @Override
        public Optional<SequenceRule> findByCode(String code) {
            return byId.values().stream().filter(rule -> rule.code().equals(code)).findFirst();
        }

        @Override
        public void insert(SequenceRule rule) {
            byId.put(rule.id(), rule);
        }

        @Override
        public void update(SequenceRule rule) {
            byId.put(rule.id(), rule);
        }
    }

    /** 内存序列号仓储（按「规则 + 周期键」计数）。 */
    static final class InMemorySequenceCounterRepository implements SequenceCounterRepository {

        final Map<String, Long> byKey = new LinkedHashMap<>();

        private static String key(String ruleCode, String periodKey) {
            return ruleCode + "|" + periodKey;
        }

        @Override
        public long next(String ruleCode, String periodKey, long step) {
            long value = byKey.getOrDefault(key(ruleCode, periodKey), 0L) + step;
            byKey.put(key(ruleCode, periodKey), value);
            return value;
        }

        @Override
        public long current(String ruleCode, String periodKey) {
            return byKey.getOrDefault(key(ruleCode, periodKey), 0L);
        }
    }
}
