package com.foundation.authz.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foundation.starter.data.BaseDO;

/** 人员维度授予实体（{@code fdn_person_dimension}）。 */
@TableName("fdn_person_dimension")
public class PersonDimensionDO extends BaseDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String personId;

    private String dimensionId;

    private String valueCode;

    private Boolean allValues;

    private String source;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPersonId() {
        return personId;
    }

    public void setPersonId(String personId) {
        this.personId = personId;
    }

    public String getDimensionId() {
        return dimensionId;
    }

    public void setDimensionId(String dimensionId) {
        this.dimensionId = dimensionId;
    }

    public String getValueCode() {
        return valueCode;
    }

    public void setValueCode(String valueCode) {
        this.valueCode = valueCode;
    }

    public Boolean getAllValues() {
        return allValues;
    }

    public void setAllValues(Boolean allValues) {
        this.allValues = allValues;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
