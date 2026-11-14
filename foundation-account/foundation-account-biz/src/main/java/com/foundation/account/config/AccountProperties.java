package com.foundation.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 账号域配置（F-04 §8，前缀 {@code foundation.account}）。
 *
 * <p>对应文档的三组：{@code provision}（开通）、{@code reconcile}（每日对账）、
 * {@code emergency-admin}（应急管理员）。
 */
@ConfigurationProperties(prefix = "foundation.account")
public class AccountProperties {

    private Provision provision = new Provision();

    private Reconcile reconcile = new Reconcile();

    private EmergencyAdmin emergencyAdmin = new EmergencyAdmin();

    public Provision getProvision() {
        return provision;
    }

    public void setProvision(Provision provision) {
        this.provision = provision;
    }

    public Reconcile getReconcile() {
        return reconcile;
    }

    public void setReconcile(Reconcile reconcile) {
        this.reconcile = reconcile;
    }

    public EmergencyAdmin getEmergencyAdmin() {
        return emergencyAdmin;
    }

    public void setEmergencyAdmin(EmergencyAdmin emergencyAdmin) {
        this.emergencyAdmin = emergencyAdmin;
    }

    /** 开通策略。 */
    public static class Provision {

        /** 开通时的必需动作（缺省首次改密）。 */
        private List<String> requiredActions = new ArrayList<>(List.of("UPDATE_PASSWORD"));

        /** 重置方式：{@code email}（发重置邮件）或 {@code temporary-password}（一次性临时口令）。 */
        private String resetVia = "email";

        /** 登录名生成规则模板。 */
        private String loginNameRule = "{employeeNo}";

        public List<String> getRequiredActions() {
            return requiredActions;
        }

        public void setRequiredActions(List<String> requiredActions) {
            this.requiredActions = requiredActions;
        }

        public String getResetVia() {
            return resetVia;
        }

        public void setResetVia(String resetVia) {
            this.resetVia = resetVia;
        }

        public String getLoginNameRule() {
            return loginNameRule;
        }

        public void setLoginNameRule(String loginNameRule) {
            this.loginNameRule = loginNameRule;
        }
    }

    /** 每日对账策略。 */
    public static class Reconcile {

        /** 对账计划（Spring cron）。 */
        private String cron = "0 30 2 * * *";

        /** 差异占比阈值（%，超过则暂停修复并告警）。 */
        private int driftThresholdPercent = 5;

        /** 差异条数阈值（超过则暂停修复并告警）。 */
        private int driftThresholdCount = 50;

        /** 暂停修复时是否告警。 */
        private boolean alertOnHold = true;

        /** 单实例锁持有时长（秒），超时自动释放。 */
        private long lockTtlSeconds = 1800;

        /** 每轮重试待发记录的条数上限。 */
        private int pushFlushLimit = 100;

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public int getDriftThresholdPercent() {
            return driftThresholdPercent;
        }

        public void setDriftThresholdPercent(int driftThresholdPercent) {
            this.driftThresholdPercent = driftThresholdPercent;
        }

        public int getDriftThresholdCount() {
            return driftThresholdCount;
        }

        public void setDriftThresholdCount(int driftThresholdCount) {
            this.driftThresholdCount = driftThresholdCount;
        }

        public boolean isAlertOnHold() {
            return alertOnHold;
        }

        public void setAlertOnHold(boolean alertOnHold) {
            this.alertOnHold = alertOnHold;
        }

        public long getLockTtlSeconds() {
            return lockTtlSeconds;
        }

        public void setLockTtlSeconds(long lockTtlSeconds) {
            this.lockTtlSeconds = lockTtlSeconds;
        }

        public int getPushFlushLimit() {
            return pushFlushLimit;
        }

        public void setPushFlushLimit(int pushFlushLimit) {
            this.pushFlushLimit = pushFlushLimit;
        }
    }

    /** 应急管理员策略。 */
    public static class EmergencyAdmin {

        /** 是否启用应急管理员（生产可关闭）。 */
        private boolean enabled = true;

        /** 启用是否需两名系统管理员先后确认（四眼）。 */
        private boolean requireTwoPerson = true;

        /** 启用申请有效期（秒）。 */
        private long requestTtlSeconds = 900;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRequireTwoPerson() {
            return requireTwoPerson;
        }

        public void setRequireTwoPerson(boolean requireTwoPerson) {
            this.requireTwoPerson = requireTwoPerson;
        }

        public long getRequestTtlSeconds() {
            return requestTtlSeconds;
        }

        public void setRequestTtlSeconds(long requestTtlSeconds) {
            this.requestTtlSeconds = requestTtlSeconds;
        }
    }
}
