package com.foundation.account.domain;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 外部身份自动关联策略（F-04 §2 规则 1、3）。
 *
 * <p>只按工号或**已验证邮箱**匹配；**匹配不唯一或无对应人员时不自动关联、不自动建人**；
 * **不允许仅凭姓名匹配**（姓名不参与本策略）。
 */
public final class IdentityLinkPolicy {

    private IdentityLinkPolicy() {
    }

    /**
     * 候选人员恰好唯一时返回该人员 ID，否则返回空（留待处理 / 管理员确认）。
     *
     * @param candidatePersonIds 按工号与已验证邮箱命中的候选人员 ID（可含空值、重复值）
     */
    public static Optional<String> uniqueCandidate(Collection<String> candidatePersonIds) {
        if (candidatePersonIds == null) {
            return Optional.empty();
        }
        Set<String> distinct = candidatePersonIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toSet());
        return distinct.size() == 1 ? Optional.of(distinct.iterator().next()) : Optional.empty();
    }
}
