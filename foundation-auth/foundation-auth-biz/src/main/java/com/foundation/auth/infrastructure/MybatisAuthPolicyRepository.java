package com.foundation.auth.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.auth.domain.AuthPolicy;
import com.foundation.auth.domain.AuthPolicyRules;
import com.foundation.auth.domain.LoginMode;
import com.foundation.auth.infrastructure.mapper.AuthLoginModeMapper;
import com.foundation.auth.infrastructure.mapper.AuthPolicyMapper;
import com.foundation.auth.infrastructure.persistence.AuthLoginModeDO;
import com.foundation.auth.infrastructure.persistence.AuthPolicyDO;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 登录策略仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisAuthPolicyRepository implements AuthPolicyRepository {

    private final AuthPolicyMapper policyMapper;
    private final AuthLoginModeMapper loginModeMapper;

    public MybatisAuthPolicyRepository(AuthPolicyMapper policyMapper, AuthLoginModeMapper loginModeMapper) {
        this.policyMapper = policyMapper;
        this.loginModeMapper = loginModeMapper;
    }

    @Override
    public Optional<AuthPolicy> find() {
        AuthPolicyDO row = policyMapper.selectOne(Wrappers.<AuthPolicyDO>lambdaQuery()
                .orderByAsc(AuthPolicyDO::getCreateAt)
                .last("limit 1"));
        return Optional.ofNullable(row).map(MybatisAuthPolicyRepository::toDomain);
    }

    @Override
    public void save(AuthPolicy policy) {
        AuthPolicyDO existing = policy.id() == null ? null : policyMapper.selectById(policy.id());
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        AuthPolicyDO row = new AuthPolicyDO();
        row.setId(policy.id());
        row.setPasswordPolicy(policy.passwordPolicy());
        row.setSessionIdleMinutes(policy.sessionIdleMinutes());
        row.setSessionMaxHours(policy.sessionMaxHours());
        row.setConcurrentSessions(policy.concurrentSessions());
        row.setTotpRequiredRoles(AuthPolicyRules.joinTotpRoles(policy.totpRequiredRoles()));
        if (existing == null) {
            row.setCreateAt(policy.createAt() == null ? now : policy.createAt());
            row.setUpdateAt(row.getCreateAt());
            policyMapper.insert(row);
            return;
        }
        row.setUpdateAt(now);
        policyMapper.updateById(row);
    }

    @Override
    public List<LoginMode> listLoginModes() {
        List<AuthLoginModeDO> rows = loginModeMapper.selectList(Wrappers.<AuthLoginModeDO>lambdaQuery()
                .orderByAsc(AuthLoginModeDO::getSortOrder)
                .orderByAsc(AuthLoginModeDO::getModeCode));
        return rows.stream().map(MybatisAuthPolicyRepository::toDomain).toList();
    }

    @Override
    public Optional<LoginMode> findLoginMode(String modeCode) {
        AuthLoginModeDO row = loginModeMapper.selectOne(Wrappers.<AuthLoginModeDO>lambdaQuery()
                .eq(AuthLoginModeDO::getModeCode, modeCode)
                .last("limit 1"));
        return Optional.ofNullable(row).map(MybatisAuthPolicyRepository::toDomain);
    }

    @Override
    public void updateLoginMode(LoginMode mode) {
        AuthLoginModeDO row = new AuthLoginModeDO();
        row.setId(mode.id());
        row.setEnabled(mode.enabled());
        row.setSortOrder(mode.sortOrder());
        row.setUpdateAt(LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
        loginModeMapper.updateById(row);
    }

    private static AuthPolicy toDomain(AuthPolicyDO row) {
        return new AuthPolicy(row.getId(), row.getPasswordPolicy(),
                row.getSessionIdleMinutes() == null ? 0 : row.getSessionIdleMinutes(),
                row.getSessionMaxHours() == null ? 0 : row.getSessionMaxHours(),
                row.getConcurrentSessions() == null ? 0 : row.getConcurrentSessions(),
                AuthPolicyRules.parseTotpRoles(row.getTotpRequiredRoles()),
                row.getCreateAt(), row.getUpdateAt());
    }

    private static LoginMode toDomain(AuthLoginModeDO row) {
        return new LoginMode(row.getId(), row.getModeCode(), row.getName(),
                Boolean.TRUE.equals(row.getEnabled()),
                row.getSortOrder() == null ? 0 : row.getSortOrder(),
                Boolean.TRUE.equals(row.getComplex()), row.getConsoleHint());
    }
}
