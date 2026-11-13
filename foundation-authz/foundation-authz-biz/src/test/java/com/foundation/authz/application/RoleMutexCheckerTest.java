package com.foundation.authz.application;

import com.foundation.authz.domain.RoleMutex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 互斥角色归一与过滤（F-05 BAS-05 §测试 {@code RoleMutexCheckerTest}）。 */
class RoleMutexCheckerTest {

    private final RoleMutexChecker checker = new RoleMutexChecker();

    @Test
    void singleDirectionalPairIsNormalizedToSymmetric() {
        List<RoleMutex> pairs = List.of(new RoleMutex("admin", "auditor"));

        assertThat(checker.conflict("admin", "auditor", pairs)).isTrue();
        assertThat(checker.conflict("auditor", "admin", pairs)).isTrue();
    }

    @Test
    void nonConflictingRolesAreKeptInOrder() {
        List<RoleMutex> pairs = List.of(new RoleMutex("admin", "auditor"));

        assertThat(checker.removeConflicts(List.of("admin", "editor", "auditor"), pairs))
                .containsExactly("admin", "editor");
    }

    @Test
    void laterConflictingRoleIsDropped() {
        List<RoleMutex> pairs = List.of(new RoleMutex("admin", "auditor"));

        assertThat(checker.removeConflicts(List.of("auditor", "admin"), pairs))
                .containsExactly("auditor");
    }

    @Test
    void withoutMutexPairsNothingIsDropped() {
        assertThat(checker.removeConflicts(List.of("admin", "auditor"), List.of()))
                .containsExactly("admin", "auditor");
    }
}
