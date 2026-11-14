package com.foundation.calendar.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 工作日历头实体（{@code fdn_calendar}，F-09 BAS-18 §18.1）。
 *
 * <p>不继承 {@code BaseDO}：本表无 {@code deleted} / 审计列，时间列为自持的
 * {@code create_at} / {@code update_at}（与 BAS-17 {@code fdn_file} 同口径）。
 */
@TableName("fdn_calendar")
public class CalendarDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String regionCode;

    private Integer year;

    private String name;

    private String source;

    private String status;

    private LocalDateTime createAt;

    private LocalDateTime updateAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

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
}
