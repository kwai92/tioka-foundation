package com.foundation.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 同步配置（F-08 BAS-11 §11 {@code foundation.sync.*}）。
 *
 * <p>与 {@code SyncAutoConfiguration} 同包（00-施工总纲 §2.5），全部带默认值。
 */
@ConfigurationProperties(prefix = "foundation.sync")
public class SyncProperties {

    /** 按需装载的适配器（ADR-078 第 8 条）；为空表示装载全部已发现的适配器。 */
    private List<String> adapters = new ArrayList<>();

    /** 精简插件宿主扫描目录（jar；签名与 SPI 版本校验随 FND-12 接入）。 */
    private String adaptersDir = "";

    /** 落地前必须预演。 */
    private boolean previewRequired = true;

    private Schedule schedule = new Schedule();

    private DeleteProtection deleteProtection = new DeleteProtection();

    private RateLimit rateLimit = new RateLimit();

    private Egress egress = new Egress();

    public List<String> getAdapters() {
        return adapters;
    }

    public void setAdapters(List<String> adapters) {
        this.adapters = adapters == null ? new ArrayList<>() : adapters;
    }

    public String getAdaptersDir() {
        return adaptersDir;
    }

    public void setAdaptersDir(String adaptersDir) {
        this.adaptersDir = adaptersDir;
    }

    public boolean isPreviewRequired() {
        return previewRequired;
    }

    public void setPreviewRequired(boolean previewRequired) {
        this.previewRequired = previewRequired;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public void setSchedule(Schedule schedule) {
        this.schedule = schedule;
    }

    public DeleteProtection getDeleteProtection() {
        return deleteProtection;
    }

    public void setDeleteProtection(DeleteProtection deleteProtection) {
        this.deleteProtection = deleteProtection;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Egress getEgress() {
        return egress;
    }

    public void setEgress(Egress egress) {
        this.egress = egress;
    }

    /** 定时同步计划（增量与每日全量对账）。 */
    public static class Schedule {

        /** 增量触发（有增量能力的来源）。 */
        private String incrementalCron = "0 */15 * * * *";

        /** 每日全量对账。 */
        private String fullReconcileCron = "0 0 3 * * *";

        public String getIncrementalCron() {
            return incrementalCron;
        }

        public void setIncrementalCron(String incrementalCron) {
            this.incrementalCron = incrementalCron;
        }

        public String getFullReconcileCron() {
            return fullReconcileCron;
        }

        public void setFullReconcileCron(String fullReconcileCron) {
            this.fullReconcileCron = fullReconcileCron;
        }
    }

    /** 删除保护阈值（F-08 §6）。 */
    public static class DeleteProtection {

        /** 一次停用人员百分比阈值。 */
        private int disablePercent = 5;

        /** 一次停用人员绝对数阈值。 */
        private int disableCount = 50;

        /** 一次删除（停用）组织单元阈值。 */
        private int orgUnitDeleteCount = 10;

        public int getDisablePercent() {
            return disablePercent;
        }

        public void setDisablePercent(int disablePercent) {
            this.disablePercent = disablePercent;
        }

        public int getDisableCount() {
            return disableCount;
        }

        public void setDisableCount(int disableCount) {
            this.disableCount = disableCount;
        }

        public int getOrgUnitDeleteCount() {
            return orgUnitDeleteCount;
        }

        public void setOrgUnitDeleteCount(int orgUnitDeleteCount) {
            this.orgUnitDeleteCount = orgUnitDeleteCount;
        }
    }

    /** 适配器限速与重试。 */
    public static class RateLimit {

        /** 适配器默认限速（QPS）。 */
        private int defaultQps = 10;

        /** 退避重试次数。 */
        private int maxRetries = 3;

        /** 退避基数（毫秒）。 */
        private long backoffMs = 500;

        public int getDefaultQps() {
            return defaultQps;
        }

        public void setDefaultQps(int defaultQps) {
            this.defaultQps = defaultQps;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public long getBackoffMs() {
            return backoffMs;
        }

        public void setBackoffMs(long backoffMs) {
            this.backoffMs = backoffMs;
        }
    }

    /** 出网与台账（F-08 §9）。 */
    public static class Egress {

        /**
         * 是否按 ADR-074 写 {@code ext_interaction} 台账。
         *
         * <p>默认关闭：本仓库尚无 ADR-074 的台账表实现（随 FND-07 出网白名单 / FND-12 插件宿主交付）。
         * 置 {@code true} 时必须同时提供 {@code SyncLedgerWriter} 的 ADR-074 实现，否则装配失败。
         */
        private boolean writeLedger = false;

        /** 是否走 FND-07 出网白名单。 */
        private boolean whitelistDriven = true;

        public boolean isWriteLedger() {
            return writeLedger;
        }

        public void setWriteLedger(boolean writeLedger) {
            this.writeLedger = writeLedger;
        }

        public boolean isWhitelistDriven() {
            return whitelistDriven;
        }

        public void setWhitelistDriven(boolean whitelistDriven) {
            this.whitelistDriven = whitelistDriven;
        }
    }
}
