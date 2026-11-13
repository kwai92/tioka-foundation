package com.foundation.starter.security.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * {@code fdn.authz.changed} 订阅（F-07 §4）：收到本应用的人员授权变更后删除其本地缓存。
 *
 * <p>订阅由 starter 自动完成（F-07 §10 第 5 步）；应用侧只声明 consumer 组名。
 * 事件主体交给 {@link AuthzSnapshotClient#evict(String)}，下次取用即回源到新版本。
 */
public class AuthzChangedListener {

    private final AuthzSnapshotClient client;
    private final String appCode;
    private final ObjectMapper objectMapper;

    public AuthzChangedListener(AuthzSnapshotClient client, String appCode, ObjectMapper objectMapper) {
        this.client = client;
        this.appCode = appCode;
        this.objectMapper = objectMapper;
    }

    /** 处理事件 JSON；返回是否实际删除了本应用的缓存条目。 */
    public boolean onMessage(String json) {
        try {
            return handle(objectMapper.readValue(json, AuthzChangedMessage.class));
        } catch (IOException ex) {
            throw new IllegalStateException("解析授权变更事件失败：" + ex.getMessage(), ex);
        }
    }

    /** 处理事件体；非本应用的事件忽略。 */
    public boolean handle(AuthzChangedMessage message) {
        if (message == null || message.personId() == null) {
            return false;
        }
        if (appCode != null && !appCode.equals(message.appCode())) {
            return false;
        }
        client.evict(message.personId());
        return true;
    }
}
