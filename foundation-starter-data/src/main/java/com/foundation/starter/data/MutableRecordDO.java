package com.foundation.starter.data;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 有状态机、需更新但不软删的表基类（F-02 BAS-24 §foundation-starter-data）：四个审计列，无 {@code deleted}。
 *
 * <p>与 {@link BaseDO} 的差别：本类不加 {@code @TableLogic}，删除一律由业务状态列表达
 * （如 {@code status='RETIRED'}），杜绝「物理 DELETE」与「静默逻辑删」并存（48-CS §4）。
 *
 * <p>时间列口径见 48-CS §4：数据库 {@code TIMESTAMPTZ}、字段 {@link LocalDateTime}（UTC 语义），
 * 不用 {@code Instant}/{@code Date}（ArchUnit 规则 {@code doTimeFieldsAreLocalDateTime}）。
 */
public abstract class MutableRecordDO implements Serializable {

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateAt;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

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
}
