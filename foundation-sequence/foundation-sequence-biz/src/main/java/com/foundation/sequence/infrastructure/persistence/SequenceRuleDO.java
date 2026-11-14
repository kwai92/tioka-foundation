package com.foundation.sequence.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 编号规则实体（{@code fdn_sequence_rule}，F-09 BAS-18 §18.2）。
 *
 * <p>不继承 {@code BaseDO}：本表无 {@code deleted} / 审计列，时间列为自持的
 * {@code create_at} / {@code update_at}（与 BAS-18 日历、BAS-17 文件同口径）。
 */
@TableName("fdn_sequence_rule")
public class SequenceRuleDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String code;

    private String name;

    private String pattern;

    private String prefix;

    private Integer seqLength;

    private Integer step;

    private String resetCycle;

    private String checkDigit;

    private String status;

    private String sample;

    private LocalDateTime createAt;

    private LocalDateTime updateAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public Integer getSeqLength() {
        return seqLength;
    }

    public void setSeqLength(Integer seqLength) {
        this.seqLength = seqLength;
    }

    public Integer getStep() {
        return step;
    }

    public void setStep(Integer step) {
        this.step = step;
    }

    public String getResetCycle() {
        return resetCycle;
    }

    public void setResetCycle(String resetCycle) {
        this.resetCycle = resetCycle;
    }

    public String getCheckDigit() {
        return checkDigit;
    }

    public void setCheckDigit(String checkDigit) {
        this.checkDigit = checkDigit;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSample() {
        return sample;
    }

    public void setSample(String sample) {
        this.sample = sample;
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
