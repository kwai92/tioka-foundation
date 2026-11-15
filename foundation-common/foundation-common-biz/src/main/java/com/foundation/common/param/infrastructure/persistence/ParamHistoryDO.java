package com.foundation.common.param.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 参数变更历史实体（{@code fdn_param_history}，F-02 BAS-09 §迁移 V2）。
 *
 * <p>{@code changed_at} 不加填充注解，靠数据库 {@code DEFAULT now()}，与原 SQL 行为一致。
 */
@TableName("fdn_param_history")
public class ParamHistoryDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String paramId;

    /** 旧值 SHA-256（首次为空）。 */
    private String oldDigest;

    private String newDigest;

    private String changedBy;

    /** 变更时间（UTC 语义，列 {@code TIMESTAMPTZ}）。 */
    private LocalDateTime changedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getParamId() {
        return paramId;
    }

    public void setParamId(String paramId) {
        this.paramId = paramId;
    }

    public String getOldDigest() {
        return oldDigest;
    }

    public void setOldDigest(String oldDigest) {
        this.oldDigest = oldDigest;
    }

    public String getNewDigest() {
        return newDigest;
    }

    public void setNewDigest(String newDigest) {
        this.newDigest = newDigest;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }
}
