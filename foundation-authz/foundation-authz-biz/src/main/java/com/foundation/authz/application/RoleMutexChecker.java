package com.foundation.authz.application;

import com.foundation.authz.domain.RoleMutex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 互斥角色校验（F-05 BAS-05 §有效角色与互斥）。
 *
 * <p>{@code fdn_role_mutex} 只存单向对，本类归一为对称关系；
 * 两个互斥角色同时命中时保留先出现的，丢弃后者并告警。
 */
public class RoleMutexChecker {

    private static final Logger LOG = LoggerFactory.getLogger(RoleMutexChecker.class);

    /** 过滤掉与已保留角色互斥的角色，保留输入顺序。 */
    public Set<String> removeConflicts(Collection<String> roleCodes, List<RoleMutex> mutexPairs) {
        Map<String, Set<String>> mutex = normalize(mutexPairs);
        Set<String> kept = new LinkedHashSet<>();
        for (String roleCode : roleCodes) {
            Set<String> conflicting = mutex.getOrDefault(roleCode, Set.of());
            if (conflicting.stream().anyMatch(kept::contains)) {
                LOG.warn("互斥角色同时命中，已丢弃后者：{}（与 {} 互斥）", roleCode, conflicting);
                continue;
            }
            kept.add(roleCode);
        }
        return kept;
    }

    /** 两个角色是否互斥（对称判定）。 */
    public boolean conflict(String left, String right, List<RoleMutex> mutexPairs) {
        if (left == null || right == null) {
            return false;
        }
        return normalize(mutexPairs).getOrDefault(left, Set.of()).contains(right);
    }

    private static Map<String, Set<String>> normalize(List<RoleMutex> mutexPairs) {
        Map<String, Set<String>> mutex = new HashMap<>();
        for (RoleMutex pair : mutexPairs) {
            mutex.computeIfAbsent(pair.roleCode(), key -> new HashSet<>()).add(pair.mutexRoleCode());
            mutex.computeIfAbsent(pair.mutexRoleCode(), key -> new HashSet<>()).add(pair.roleCode());
        }
        return mutex;
    }
}
