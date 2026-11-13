package com.foundation.starter.data;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 行级审计基类（F-02 BAS-24 §foundation-starter-data）。
 *
 * <p>字段口径见 48-CS §4「行级字段」：{@code createAt}/{@code updateAt}/{@code createBy}/{@code updateBy}/
 * {@code deleted}，禁止 {@code tenantId}（ADR-029 第 60 条）。时间字段只用 {@link LocalDateTime}
 * （ArchUnit 规则 {@code doTimeFieldsAreLocalDateTime}）。
 *
 * <p>主键不在此声明：既有 {@code id} 型主键，也有 {@code code} 型自然主键（如 {@code fdn_role}）。
 *
 * <p>本类为 BAS-24 抽取的雏形，{@code MutableRecordDO}/{@code AppendOnlyDO}/{@code ImmutableSnapshotDO}
 * 与乐观锁约定随 BAS-24 补齐。
 */
public abstract class BaseDO implements Serializable {

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateAt;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /** 逻辑删除列（48-CS §4：主数据禁止物理 DELETE）。布尔列，取值 {@code true}/{@code false}。 */
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted;

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }

    public LocalDateTime getUpdateAt() {
        return updateAt;
    }

    public void setUpdateAt(LocalDateTime updateAt) {
        this.updateAt = updateAt;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }
}
