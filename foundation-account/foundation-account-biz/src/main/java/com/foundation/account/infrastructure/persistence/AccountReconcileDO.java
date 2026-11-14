package com.foundation.account.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 每日对账记录实体（{@code fdn_account_reconcile}，F-04 §6）：只追加，无逻辑删与 {@code update_*} 列，
 * 故不继承 {@code BaseDO}。
 */
@TableName("fdn_account_reconcile")
public class AccountReconcileDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private LocalDateTime ranAt;

    private Integer total;

    private Integer drifted;

    private Integer fixed;

    private Boolean held;

    private String detailJson;

    private LocalDateTime createAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalDateTime getRanAt() {
        return ranAt;
    }

    public void setRanAt(LocalDateTime ranAt) {
        this.ranAt = ranAt;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Integer getDrifted() {
        return drifted;
    }

    public void setDrifted(Integer drifted) {
        this.drifted = drifted;
    }

    public Integer getFixed() {
        return fixed;
    }

    public void setFixed(Integer fixed) {
        this.fixed = fixed;
    }

    public Boolean getHeld() {
        return held;
    }

    public void setHeld(Boolean held) {
        this.held = held;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public void setDetailJson(String detailJson) {
        this.detailJson = detailJson;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }
}
