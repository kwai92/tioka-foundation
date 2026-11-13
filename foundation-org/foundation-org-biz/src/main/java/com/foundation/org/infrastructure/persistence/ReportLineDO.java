package com.foundation.org.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foundation.starter.data.BaseDO;

/** 汇报关系实体（{@code fdn_report_line}）：主键为 {@code person_id}。 */
@TableName("fdn_report_line")
public class ReportLineDO extends BaseDO {

    @TableId(value = "person_id", type = IdType.INPUT)
    private String personId;

    private String managerPersonId;

    private String source;

    public String getPersonId() {
        return personId;
    }

    public void setPersonId(String personId) {
        this.personId = personId;
    }

    public String getManagerPersonId() {
        return managerPersonId;
    }

    public void setManagerPersonId(String managerPersonId) {
        this.managerPersonId = managerPersonId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
