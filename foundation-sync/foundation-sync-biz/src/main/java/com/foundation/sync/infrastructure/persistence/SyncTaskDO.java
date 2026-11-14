package com.foundation.sync.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 同步任务实体（{@code fdn_sync_task}，F-08 BAS-11 §8）。
 *
 * <p>无 {@code deleted} 列：运行期记录，不参与逻辑删。
 */
@TableName("fdn_sync_task")
public class SyncTaskDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String sourceId;

    private String mode;

    private String triggerMode;

    private String status;

    private Boolean dryRun;

    private String cursorJson;

    private Integer createdCount;

    private Integer updatedCount;

    private Integer disabledCount;

    private Integer movedCount;

    private Boolean capabilityMissing;

    private String pauseReason;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private String createBy;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getTriggerMode() {
        return triggerMode;
    }

    public void setTriggerMode(String triggerMode) {
        this.triggerMode = triggerMode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getDryRun() {
        return dryRun;
    }

    public void setDryRun(Boolean dryRun) {
        this.dryRun = dryRun;
    }

    public String getCursorJson() {
        return cursorJson;
    }

    public void setCursorJson(String cursorJson) {
        this.cursorJson = cursorJson;
    }

    public Integer getCreatedCount() {
        return createdCount;
    }

    public void setCreatedCount(Integer createdCount) {
        this.createdCount = createdCount;
    }

    public Integer getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(Integer updatedCount) {
        this.updatedCount = updatedCount;
    }

    public Integer getDisabledCount() {
        return disabledCount;
    }

    public void setDisabledCount(Integer disabledCount) {
        this.disabledCount = disabledCount;
    }

    public Integer getMovedCount() {
        return movedCount;
    }

    public void setMovedCount(Integer movedCount) {
        this.movedCount = movedCount;
    }

    public Boolean getCapabilityMissing() {
        return capabilityMissing;
    }

    public void setCapabilityMissing(Boolean capabilityMissing) {
        this.capabilityMissing = capabilityMissing;
    }

    public String getPauseReason() {
        return pauseReason;
    }

    public void setPauseReason(String pauseReason) {
        this.pauseReason = pauseReason;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }
}
