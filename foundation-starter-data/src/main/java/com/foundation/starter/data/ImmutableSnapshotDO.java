package com.foundation.starter.data;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 已发布不可变快照表基类（F-02 BAS-24 §foundation-starter-data）：仅创建审计，无逻辑删、无更新填充。
 *
 * <p>适用于「发布即冻结」的对象：授权快照、权限目录版本、配置版本等。行一旦写入，
 * 任何修改都必须产生新版本行，因此库层只允许 INSERT / SELECT；更新填充位不存在，
 * 也让误写 UPDATE 在代码评审与测试中更显眼。
 *
 * <p>时间列口径见 48-CS §4：{@code TIMESTAMPTZ} + {@link LocalDateTime}（UTC 语义）。
 */
public abstract class ImmutableSnapshotDO implements Serializable {

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createAt;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }
}
