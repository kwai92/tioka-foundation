package com.foundation.starter.data;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 高频追加日志 / 会话票据表基类（F-02 BAS-24 §foundation-starter-data）：仅创建审计，无逻辑删。
 *
 * <p>适用于只写入、不改写的事件流与流式日志（如审计事件、outbox、消费幂等记录）。
 * 这类表不给 {@code updateAt}/{@code updateBy} 填充位，一旦行已落库即视为终态，
 * 避免「日志被更新」破坏不可篡改语义（M4-06 审计口径）。
 *
 * <p>时间列口径见 48-CS §4：{@code TIMESTAMPTZ} + {@link LocalDateTime}（UTC 语义）。
 */
public abstract class AppendOnlyDO implements Serializable {

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
