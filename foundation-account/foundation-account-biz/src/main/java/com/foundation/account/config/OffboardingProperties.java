package com.foundation.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 停用与离职联动配置（F-04 BAS-14 §5，前缀 {@code foundation.offboarding}）。
 */
@ConfigurationProperties(prefix = "foundation.offboarding")
public class OffboardingProperties {

    /** 会话吊销的验收口径（秒）：停用提交成功到会话失效的上限（F-04 §3）。 */
    private long sessionRevokeDeadlineSeconds = 60;

    /** 停用账号时是否连带吊销会话（F-04 §1 第 2 步）。 */
    private boolean revokeOnDisable = true;

    /** 是否把服务账号与负责事项放入交接待办队列（F-04 §1 第 3 步）。 */
    private boolean createHandoverTodo = true;

    /** 交接待办的通知渠道（复用 BAS-16 消息中心）。 */
    private List<String> notify = new ArrayList<>(List.of("inapp", "feishu"));

    /** 事件投递主题（S15 §14.2）；实际投递主题以 {@code foundation.event.topic} 为准。 */
    private String eventTopic = "fdn-events";

    public long getSessionRevokeDeadlineSeconds() {
        return sessionRevokeDeadlineSeconds;
    }

    public void setSessionRevokeDeadlineSeconds(long sessionRevokeDeadlineSeconds) {
        this.sessionRevokeDeadlineSeconds = sessionRevokeDeadlineSeconds;
    }

    public boolean isRevokeOnDisable() {
        return revokeOnDisable;
    }

    public void setRevokeOnDisable(boolean revokeOnDisable) {
        this.revokeOnDisable = revokeOnDisable;
    }

    public boolean isCreateHandoverTodo() {
        return createHandoverTodo;
    }

    public void setCreateHandoverTodo(boolean createHandoverTodo) {
        this.createHandoverTodo = createHandoverTodo;
    }

    public List<String> getNotify() {
        return notify;
    }

    public void setNotify(List<String> notify) {
        this.notify = notify;
    }

    public String getEventTopic() {
        return eventTopic;
    }

    public void setEventTopic(String eventTopic) {
        this.eventTopic = eventTopic;
    }
}
