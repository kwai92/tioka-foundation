package com.foundation.account.infrastructure;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * 应急管理员启用申请一次性凭据存储（F-04 §7 双人确认第一步；S15 §9.4）。
 *
 * <p>基于 Redis 短时存储「申请 ID → （账号 ID，发起人）」；第二步确认时**一次性取走**（读取即删除），
 * 保证同一申请不可重复确认。生产可关闭应急管理员，本存储仅在启用时使用。
 */
public class EmergencyEnableStore {

    /** 键前缀。 */
    static final String KEY_PREFIX = "fdn:account:emergency:";

    /** 值分隔符（账号 ID 与发起人）。 */
    private static final String SEPARATOR = "\n";

    private final StringRedisTemplate redis;

    public EmergencyEnableStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 记录一条启用申请。 */
    public void save(String requestId, String accountId, String requester, Duration ttl) {
        redis.opsForValue().set(KEY_PREFIX + requestId, accountId + SEPARATOR + nullToEmpty(requester), ttl);
    }

    /** 一次性取走申请（读取并删除）。 */
    public Optional<EmergencyEnableRequest> take(String requestId) {
        String value = redis.opsForValue().getAndDelete(KEY_PREFIX + requestId);
        if (value == null) {
            return Optional.empty();
        }
        int idx = value.indexOf(SEPARATOR);
        if (idx < 0) {
            return Optional.of(new EmergencyEnableRequest(requestId, value, null));
        }
        String accountId = value.substring(0, idx);
        String requester = value.substring(idx + SEPARATOR.length());
        return Optional.of(new EmergencyEnableRequest(requestId, accountId,
                requester.isEmpty() ? null : requester));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 启用申请。
     *
     * @param requestId 申请 ID
     * @param accountId 应急管理员账号 ID
     * @param requester 发起人（第二步确认需另一名管理员）
     */
    public record EmergencyEnableRequest(String requestId, String accountId, String requester) {
    }
}
