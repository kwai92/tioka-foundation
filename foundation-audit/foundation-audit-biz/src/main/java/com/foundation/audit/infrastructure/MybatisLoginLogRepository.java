package com.foundation.audit.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.audit.api.LoginLogFailureStat;
import com.foundation.audit.api.LoginLogQuery;
import com.foundation.audit.domain.LoginLog;
import com.foundation.audit.infrastructure.mapper.LoginLogMapper;
import com.foundation.audit.infrastructure.persistence.LoginLogDO;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 登录日志仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisLoginLogRepository implements LoginLogRepository {

    private final LoginLogMapper mapper;

    public MybatisLoginLogRepository(LoginLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean saveIfAbsent(LoginLog log) {
        Long existing = mapper.selectCount(Wrappers.<LoginLogDO>lambdaQuery()
                .eq(LoginLogDO::getEventId, log.eventId()));
        if (existing != null && existing > 0) {
            return false;
        }
        try {
            mapper.insert(toDO(log));
            return true;
        } catch (DuplicateKeyException e) {
            // 并发下同 event_id 已被插入：视为已存在
            return false;
        }
    }

    @Override
    public List<LoginLog> page(LoginLogQuery query) {
        int page = query.page() <= 0 ? 1 : query.page();
        int size = query.size() <= 0 ? 20 : Math.min(query.size(), 100);
        long offset = (long) (page - 1) * size;
        return mapper.page(blankToNull(query.loginName()), blankToNull(query.personId()), query.success(),
                        toUtc(query.from()), toUtc(query.to()), size, offset).stream()
                .map(MybatisLoginLogRepository::toDomain).toList();
    }

    @Override
    public long count(LoginLogQuery query) {
        return mapper.count(blankToNull(query.loginName()), blankToNull(query.personId()), query.success(),
                toUtc(query.from()), toUtc(query.to()));
    }

    @Override
    public List<LoginLogFailureStat> failureStats(String loginName, Instant from, Instant to) {
        return mapper.failureStats(blankToNull(loginName), toUtc(from), toUtc(to)).stream()
                .map(MybatisLoginLogRepository::toStat).toList();
    }

    @Override
    public Optional<Instant> latestTs() {
        LoginLogDO row = mapper.selectOne(Wrappers.<LoginLogDO>lambdaQuery()
                .orderByDesc(LoginLogDO::getTs)
                .last("LIMIT 1"));
        return row == null || row.getTs() == null
                ? Optional.empty()
                : Optional.of(row.getTs().toInstant(ZoneOffset.UTC));
    }

    private static LoginLogFailureStat toStat(Map<String, Object> row) {
        Object loginName = row.get("login_name");
        Object failures = row.get("failures");
        return new LoginLogFailureStat(loginName == null ? null : loginName.toString(),
                failures == null ? 0L : ((Number) failures).longValue());
    }

    private static LoginLogDO toDO(LoginLog log) {
        LoginLogDO row = new LoginLogDO();
        row.setId(log.id());
        row.setEventId(log.eventId());
        row.setAccountId(log.accountId());
        row.setLoginName(log.loginName());
        row.setPersonId(log.personId());
        row.setKeycloakUserId(log.keycloakUserId());
        row.setSuccess(log.success());
        row.setAuthMethod(log.authMethod());
        row.setClientId(log.clientId());
        row.setFailureReason(log.failureReason());
        row.setClientIp(log.clientIp());
        row.setUserAgent(log.userAgent());
        row.setTs(toUtc(log.ts()));
        return row;
    }

    private static LoginLog toDomain(LoginLogDO row) {
        return new LoginLog(row.getId(), row.getEventId(), row.getAccountId(), row.getLoginName(),
                row.getPersonId(), row.getKeycloakUserId(), Boolean.TRUE.equals(row.getSuccess()),
                row.getAuthMethod(), row.getClientId(), row.getFailureReason(), row.getClientIp(),
                row.getUserAgent(), row.getTs().toInstant(ZoneOffset.UTC));
    }

    private static LocalDateTime toUtc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
